package com.vidasalud.kafkaadmin.service;

import com.vidasalud.kafkaadmin.config.KafkaAdminProperties;
import com.vidasalud.kafkaadmin.dto.TopicRequest;
import com.vidasalud.kafkaadmin.dto.TopicSummary;
import com.vidasalud.kafkaadmin.exception.ConflictException;
import com.vidasalud.kafkaadmin.exception.KafkaUnavailableException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.CreatePartitionsResult;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaAdminServiceTest {

    private Admin admin;
    private KafkaAdminService service;

    @BeforeEach
    void setUp() {
        admin = mock(Admin.class);
        service = new KafkaAdminService(admin,
                new KafkaAdminProperties(2, List.of("vidasalud.citas", "vidasalud.alertas")));
    }

    private static <T> KafkaFuture<T> fallo(Throwable error) {
        KafkaFutureImpl<T> futuro = new KafkaFutureImpl<>();
        futuro.completeExceptionally(error);
        return futuro;
    }

    private static TopicDescription topicCon(String nombre, int particiones) {
        Node nodo = new Node(1, "localhost", 9092);
        List<TopicPartitionInfo> infos = new java.util.ArrayList<>();
        for (int i = 0; i < particiones; i++) {
            infos.add(new TopicPartitionInfo(i, nodo, List.of(nodo), List.of(nodo)));
        }
        return new TopicDescription(nombre, false, infos);
    }

    private void existeElTopic(String nombre, int particiones) {
        DescribeTopicsResult resultado = mock(DescribeTopicsResult.class);
        when(resultado.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of(nombre, topicCon(nombre, particiones))));
        when(admin.describeTopics(anyCollection())).thenReturn(resultado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void crearTopicEnviaParticionesReplicasYConfiguraciones() {
        CreateTopicsResult resultado = mock(CreateTopicsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.createTopics(anyCollection())).thenReturn(resultado);

        TopicSummary creado = service.crearTopic(new TopicRequest("pruebas.demo", 3, (short) 1,
                Map.of("retention.ms", "86400000", "cleanup.policy", "delete")));

        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(captor.capture());
        NewTopic enviado = captor.getValue().iterator().next();
        assertEquals("pruebas.demo", enviado.name());
        assertEquals(3, enviado.numPartitions());
        assertEquals(1, enviado.replicationFactor());
        assertEquals("86400000", enviado.configs().get("retention.ms"));
        assertEquals("delete", enviado.configs().get("cleanup.policy"));
        assertEquals("pruebas.demo", creado.name());
        assertEquals(3, creado.partitions());
    }

    @Test
    void crearTopicRechazaConfiguracionesNoPermitidas() {
        assertThrows(IllegalArgumentException.class, () -> service.crearTopic(new TopicRequest(
                "pruebas.demo", 1, (short) 1, Map.of("unclean.leader.election.enable", "true"))));

        verify(admin, never()).createTopics(anyCollection());
    }

    @Test
    void crearUnTopicQueYaExisteEsUnConflicto() {
        CreateTopicsResult resultado = mock(CreateTopicsResult.class);
        when(resultado.all()).thenReturn(fallo(new TopicExistsException("ya existe")));
        when(admin.createTopics(anyCollection())).thenReturn(resultado);

        assertThrows(ConflictException.class,
                () -> service.crearTopic(new TopicRequest("vidasalud.citas", 3, (short) 1, null)));
    }

    @Test
    void siKafkaNoRespondeSeInformaQueNoEstaDisponible() {
        CreateTopicsResult resultado = mock(CreateTopicsResult.class);
        when(resultado.all()).thenReturn(fallo(new TimeoutException("sin respuesta")));
        when(admin.createTopics(anyCollection())).thenReturn(resultado);

        assertThrows(KafkaUnavailableException.class,
                () -> service.crearTopic(new TopicRequest("pruebas.demo", 1, (short) 1, null)));
    }

    @Test
    void losTopicsInternosNoSePuedenEliminar() {
        assertThrows(IllegalArgumentException.class, () -> service.eliminarTopic("__consumer_offsets", true));

        verify(admin, never()).deleteTopics(anyCollection());
    }

    @Test
    void unTopicDelNegocioExigeForceParaEliminarse() {
        assertThrows(ConflictException.class, () -> service.eliminarTopic("vidasalud.citas", false));

        verify(admin, never()).deleteTopics(anyCollection());
    }

    @Test
    void conForceSeEliminaElTopicProtegido() {
        existeElTopic("vidasalud.citas", 3);
        DeleteTopicsResult resultado = mock(DeleteTopicsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.deleteTopics(anyCollection())).thenReturn(resultado);

        service.eliminarTopic("vidasalud.citas", true);

        verify(admin).deleteTopics(List.of("vidasalud.citas"));
    }

    @Test
    void unTopicNormalSeEliminaSinForce() {
        existeElTopic("pruebas.demo", 1);
        DeleteTopicsResult resultado = mock(DeleteTopicsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.deleteTopics(anyCollection())).thenReturn(resultado);

        service.eliminarTopic("pruebas.demo", false);

        verify(admin).deleteTopics(List.of("pruebas.demo"));
    }

    @Test
    void lasParticionesSoloSePuedenAumentar() {
        existeElTopic("pruebas.demo", 3);

        assertThrows(IllegalArgumentException.class, () -> service.aumentarParticiones("pruebas.demo", 3));
        assertThrows(IllegalArgumentException.class, () -> service.aumentarParticiones("pruebas.demo", 2));
        verify(admin, never()).createPartitions(anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aumentarParticionesPideElTotalNuevo() {
        existeElTopic("pruebas.demo", 3);
        CreatePartitionsResult resultado = mock(CreatePartitionsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.createPartitions(anyMap())).thenReturn(resultado);

        TopicSummary resumen = service.aumentarParticiones("pruebas.demo", 5);

        ArgumentCaptor<Map<String, NewPartitions>> captor = ArgumentCaptor.forClass(Map.class);
        verify(admin).createPartitions(captor.capture());
        assertEquals(5, captor.getValue().get("pruebas.demo").totalCount());
        assertEquals(5, resumen.partitions());
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualizarConfiguracionAplicaOperacionesSet() {
        existeElTopic("pruebas.demo", 3);
        AlterConfigsResult alterado = mock(AlterConfigsResult.class);
        when(alterado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.incrementalAlterConfigs(anyMap())).thenReturn(alterado);
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, "pruebas.demo");
        DescribeConfigsResult configs = mock(DescribeConfigsResult.class);
        when(configs.all()).thenReturn(KafkaFuture.completedFuture(
                Map.of(recurso, new Config(List.of(new ConfigEntry("retention.ms", "1000"))))));
        when(admin.describeConfigs(anyCollection())).thenReturn(configs);

        var info = service.actualizarConfig("pruebas.demo", Map.of("retention.ms", "1000"));

        ArgumentCaptor<Map<ConfigResource, Collection<AlterConfigOp>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(admin).incrementalAlterConfigs(captor.capture());
        AlterConfigOp operacion = captor.getValue().get(recurso).iterator().next();
        assertEquals(AlterConfigOp.OpType.SET, operacion.opType());
        assertEquals("retention.ms", operacion.configEntry().name());
        assertEquals("1000", operacion.configEntry().value());
        assertEquals("1000", info.configs().get("retention.ms"));
        assertEquals(3, info.partitions());
    }

    @Test
    void nombresInvalidosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> service.obtenerTopic("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> service.eliminarTopic("a b", false));
    }
}
