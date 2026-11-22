package com.vidasalud.streams.config;

import com.vidasalud.streams.topology.CitasTopology;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafkaStreams   // crea el StreamsBuilder y arranca Kafka Streams con spring.kafka.streams.*
@EnableConfigurationProperties(StreamsProperties.class)
public class StreamsWiring {

    /** Registra la topologia del pipeline en el StreamsBuilder que gestiona Spring. */
    @Autowired
    void registrarTopologia(StreamsBuilder builder, StreamsProperties props) {
        CitasTopology.build(builder, props);
    }

    // Los topics se crean al arrancar (la creación automática del broker está desactivada),
    // cada uno con la configuración que pide su flujo de datos.

    /** Alertas: log de eventos, se borran a los 30 días. */
    @Bean
    public NewTopic alertasTopic(StreamsProperties p) {
        return TopicBuilder.name(p.alertsTopic())
                .partitions(p.partitions()).replicas(p.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(p.alertsRetentionMs()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(p.minInsyncReplicas()))
                .build();
    }

    /** Total por paciente: solo importa el último valor de cada key -> compactación. */
    @Bean
    public NewTopic citasPorPacienteTopic(StreamsProperties p) {
        return TopicBuilder.name(p.porPacienteTopic())
                .partitions(p.partitions()).replicas(p.replicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(p.minInsyncReplicas()))
                .build();
    }

    /** Entrada: idempotente con la que crea appointments (mismo nombre, particiones y retención). */
    @Bean
    public NewTopic citasEntradaTopic(StreamsProperties p) {
        return TopicBuilder.name(p.inputTopic())
                .partitions(p.partitions()).replicas(p.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(p.minInsyncReplicas()))
                .build();
    }
}
