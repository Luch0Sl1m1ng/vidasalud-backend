package com.vidasalud.appointments.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Crea el topic de las citas al arrancar (equivale a "Crear el Topic con Particiones"
 * de la guía) con su configuración de administración: particiones, factor de replicación,
 * retención y política de limpieza. KafkaAdmin lo crea solo si todavía no existe.
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaTopicConfig {

    @Bean
    @ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
    public NewTopic citasTopic(KafkaTopicProperties props) {
        return TopicBuilder.name(props.topic())
                .partitions(props.partitions())
                .replicas(props.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(props.retentionMs()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, props.cleanupPolicy())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(props.minInsyncReplicas()))
                .build();
    }
}
