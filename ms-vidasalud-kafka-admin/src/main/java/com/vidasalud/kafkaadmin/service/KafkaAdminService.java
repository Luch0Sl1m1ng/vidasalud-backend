package com.vidasalud.kafkaadmin.service;

import com.vidasalud.kafkaadmin.config.KafkaAdminProperties;
import com.vidasalud.kafkaadmin.dto.ClusterInfoResponse;
import com.vidasalud.kafkaadmin.dto.ConsumerGroupResponse;
import com.vidasalud.kafkaadmin.dto.ConsumerGroupSummary;
import com.vidasalud.kafkaadmin.dto.NamePolicy;
import com.vidasalud.kafkaadmin.dto.TopicInfoResponse;
import com.vidasalud.kafkaadmin.dto.TopicRequest;
import com.vidasalud.kafkaadmin.dto.TopicSummary;
import com.vidasalud.kafkaadmin.exception.ConflictException;
import com.vidasalud.kafkaadmin.exception.KafkaOperationException;
import com.vidasalud.kafkaadmin.exception.KafkaUnavailableException;
import com.vidasalud.kafkaadmin.exception.ResourceNotFoundException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.DisconnectException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Administración de Kafka sobre el AdminClient: topics (crear, listar, describir, modificar
 * configuración, aumentar particiones, eliminar), consumer groups (listar, describir con offsets y lag)
 * e información del cluster. No tiene nada de HTTP: el controller solo valida y delega aquí.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private final Admin admin;
    private final KafkaAdminProperties props;

    public KafkaAdminService(Admin admin, KafkaAdminProperties props) {
        this.admin = admin;
        this.props = props;
    }

    // ---------------------------------------------------------------- cluster

    public ClusterInfoResponse cluster() {
        DescribeClusterResult result = admin.describeCluster();
        String clusterId = esperar(result.clusterId(), "el cluster");
        Node controller = esperar(result.controller(), "el cluster");
        Collection<Node> nodos = esperar(result.nodes(), "el cluster");
        List<ClusterInfoResponse.Broker> brokers = nodos.stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new ClusterInfoResponse.Broker(n.id(), n.host(), n.port()))
                .toList();
        return new ClusterInfoResponse(clusterId, controller == null ? -1 : controller.id(), brokers);
    }

    // ----------------------------------------------------------------- topics

    public List<TopicSummary> listarTopics() {
        Set<String> nombres = esperar(admin.listTopics(new ListTopicsOptions().listInternal(true)).names(), "los topics");
        if (nombres.isEmpty()) {
            return List.of();
        }
        Map<String, TopicDescription> descripciones =
                esperar(admin.describeTopics(nombres).allTopicNames(), "los topics");
        return descripciones.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(d -> new TopicSummary(d.name(), d.isInternal(), d.partitions().size(), factorReplicacion(d)))
                .toList();
    }

    public TopicInfoResponse obtenerTopic(String name) {
        validarNombre(name);
        TopicDescription descripcion = describir(name);

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, name);
        Config config = esperar(admin.describeConfigs(List.of(recurso)).all(), "el topic '" + name + "'").get(recurso);

        Map<String, String> configs = new TreeMap<>();
        Set<String> sobrescritas = new HashSet<>();
        if (config != null) {
            for (ConfigEntry entrada : config.entries()) {
                configs.put(entrada.name(), Objects.toString(entrada.value(), ""));
                if (entrada.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG) {
                    sobrescritas.add(entrada.name());
                }
            }
        }

        List<TopicInfoResponse.PartitionInfo> particiones = descripcion.partitions().stream()
                .sorted(Comparator.comparingInt(TopicPartitionInfo::partition))
                .map(p -> new TopicInfoResponse.PartitionInfo(
                        p.partition(),
                        p.leader() == null ? -1 : p.leader().id(),
                        p.replicas().stream().map(Node::id).toList(),
                        p.isr().stream().map(Node::id).toList()))
                .toList();

        return new TopicInfoResponse(name, descripcion.isInternal(), particiones.size(),
                factorReplicacion(descripcion), particiones, configs, sobrescritas);
    }

    public TopicSummary crearTopic(TopicRequest request) {
        validarNombre(request.name());
        Map<String, String> configs = TopicConfigPolicy.validar(request.configs());

        NewTopic nuevo = new NewTopic(request.name(), request.partitions(), request.replicationFactor());
        if (!configs.isEmpty()) {
            nuevo.configs(configs);
        }
        esperar(admin.createTopics(List.of(nuevo)).all(), "topic '" + request.name() + "'");

        log.info("[KAFKA-ADMIN] topic creado name={} particiones={} replicas={} configs={}",
                request.name(), request.partitions(), request.replicationFactor(), configs);
        return new TopicSummary(request.name(), false, request.partitions(), request.replicationFactor());
    }

    public TopicInfoResponse actualizarConfig(String name, Map<String, String> configsSolicitadas) {
        validarNombre(name);
        Map<String, String> configs = TopicConfigPolicy.validar(configsSolicitadas);
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos una configuración");
        }
        describir(name); // 404 si no existe

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, name);
        Collection<AlterConfigOp> operaciones = new ArrayList<>();
        configs.forEach((clave, valor) ->
                operaciones.add(new AlterConfigOp(new ConfigEntry(clave, valor), AlterConfigOp.OpType.SET)));
        Map<ConfigResource, Collection<AlterConfigOp>> cambios = new HashMap<>();
        cambios.put(recurso, operaciones);
        esperar(admin.incrementalAlterConfigs(cambios).all(), "topic '" + name + "'");

        log.info("[KAFKA-ADMIN] configuración actualizada topic={} cambios={}", name, configs);
        return obtenerTopic(name);
    }

    /** Solo se pueden AUMENTAR las particiones (Kafka no permite bajarlas). */
    public TopicSummary aumentarParticiones(String name, int total) {
        validarNombre(name);
        TopicDescription actual = describir(name);
        int particionesActuales = actual.partitions().size();
        if (total <= particionesActuales) {
            throw new IllegalArgumentException("El topic tiene " + particionesActuales
                    + " particiones; solo se pueden aumentar (pediste " + total + ")");
        }
        Map<String, NewPartitions> cambios = new HashMap<>();
        cambios.put(name, NewPartitions.increaseTo(total));
        esperar(admin.createPartitions(cambios).all(), "topic '" + name + "'");

        log.info("[KAFKA-ADMIN] particiones aumentadas topic={} {} -> {}", name, particionesActuales, total);
        return new TopicSummary(name, actual.isInternal(), total, factorReplicacion(actual));
    }

    public void eliminarTopic(String name, boolean force) {
        validarNombre(name);
        if (name.startsWith("_")) {
            throw new IllegalArgumentException("Los topics internos (que empiezan con _) no se pueden eliminar");
        }
        if (props.protectedTopics().contains(name) && !force) {
            throw new ConflictException("'" + name + "' es un topic del negocio (protegido). "
                    + "Si de verdad quieres eliminarlo repite la petición con ?force=true");
        }
        describir(name); // 404 si no existe
        esperar(admin.deleteTopics(List.of(name)).all(), "topic '" + name + "'");
        log.warn("[KAFKA-ADMIN] topic ELIMINADO name={} force={}", name, force);
    }

    // ---------------------------------------------------------- consumer groups

    public List<ConsumerGroupSummary> listarGrupos() {
        Collection<ConsumerGroupListing> grupos = esperar(admin.listConsumerGroups().all(), "los consumer groups");
        return grupos.stream()
                .sorted(Comparator.comparing(ConsumerGroupListing::groupId))
                .map(g -> new ConsumerGroupSummary(g.groupId(), g.state().map(Enum::name).orElse("UNKNOWN")))
                .toList();
    }

    public ConsumerGroupResponse obtenerGrupo(String groupId) {
        validarNombre(groupId);
        String contexto = "consumer group '" + groupId + "'";

        ConsumerGroupDescription descripcion =
                esperar(admin.describeConsumerGroups(List.of(groupId)).all(), contexto).get(groupId);
        Map<TopicPartition, OffsetAndMetadata> confirmados =
                esperar(admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata(), contexto);

        if (descripcion == null || (descripcion.state() == ConsumerGroupState.DEAD && confirmados.isEmpty())) {
            throw new ResourceNotFoundException("No existe el " + contexto);
        }

        List<ConsumerGroupResponse.Member> miembros = descripcion.members().stream()
                .sorted(Comparator.comparing(MemberDescription::consumerId))
                .map(m -> new ConsumerGroupResponse.Member(m.consumerId(), m.clientId(), m.host(),
                        m.assignment().topicPartitions().stream()
                                .sorted(Comparator.comparing(TopicPartition::topic)
                                        .thenComparingInt(TopicPartition::partition))
                                .map(tp -> tp.topic() + "-" + tp.partition())
                                .toList()))
                .toList();

        List<ConsumerGroupResponse.OffsetInfo> offsets = new ArrayList<>();
        long lagTotal = 0;
        if (!confirmados.isEmpty()) {
            Map<TopicPartition, OffsetSpec> pedido = new HashMap<>();
            confirmados.keySet().forEach(tp -> pedido.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> finales =
                    esperar(admin.listOffsets(pedido).all(), contexto);

            for (Map.Entry<TopicPartition, OffsetAndMetadata> e : confirmados.entrySet()) {
                if (e.getValue() == null || finales.get(e.getKey()) == null) {
                    continue;
                }
                long confirmado = e.getValue().offset();
                long fin = finales.get(e.getKey()).offset();
                long lag = Math.max(0, fin - confirmado);
                lagTotal += lag;
                offsets.add(new ConsumerGroupResponse.OffsetInfo(
                        e.getKey().topic(), e.getKey().partition(), confirmado, fin, lag));
            }
            offsets.sort(Comparator.comparing(ConsumerGroupResponse.OffsetInfo::topic)
                    .thenComparingInt(ConsumerGroupResponse.OffsetInfo::partition));
        }

        return new ConsumerGroupResponse(groupId, descripcion.state().name(), descripcion.partitionAssignor(),
                miembros, offsets, lagTotal);
    }

    // ---------------------------------------------------------------- helpers

    private TopicDescription describir(String name) {
        Map<String, TopicDescription> mapa =
                esperar(admin.describeTopics(List.of(name)).allTopicNames(), "topic '" + name + "'");
        TopicDescription descripcion = mapa.get(name);
        if (descripcion == null) {
            throw new ResourceNotFoundException("No existe el topic '" + name + "'");
        }
        return descripcion;
    }

    private static int factorReplicacion(TopicDescription d) {
        return d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size();
    }

    private static void validarNombre(String nombre) {
        if (!NamePolicy.esNombreValido(nombre)) {
            throw new IllegalArgumentException("Nombre inválido: " + NamePolicy.TOPIC_MESSAGE);
        }
    }

    /** Espera el resultado de una operación del AdminClient y traduce los errores de Kafka a errores de la API. */
    private <T> T esperar(KafkaFuture<T> futuro, String contexto) {
        try {
            return futuro.get(props.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            throw traducir(ex.getCause() != null ? ex.getCause() : ex, contexto);
        } catch (TimeoutException ex) {
            throw new KafkaUnavailableException("Kafka no respondió a tiempo", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaUnavailableException("La operación fue interrumpida", ex);
        }
    }

    private RuntimeException traducir(Throwable causa, String contexto) {
        if (causa instanceof TopicExistsException) {
            return new ConflictException("Ya existe el " + contexto);
        }
        if (causa instanceof UnknownTopicOrPartitionException || causa instanceof GroupIdNotFoundException) {
            return new ResourceNotFoundException("No existe el " + contexto);
        }
        if (causa instanceof InvalidReplicationFactorException || causa instanceof InvalidPartitionsException
                || causa instanceof InvalidTopicException || causa instanceof InvalidConfigurationException
                || causa instanceof InvalidRequestException) {
            return new IllegalArgumentException(causa.getMessage());
        }
        if (causa instanceof org.apache.kafka.common.errors.TimeoutException || causa instanceof DisconnectException) {
            return new KafkaUnavailableException("Kafka no está disponible", causa);
        }
        return new KafkaOperationException("Kafka rechazó la operación sobre " + contexto + ": " + causa.getMessage(), causa);
    }
}
