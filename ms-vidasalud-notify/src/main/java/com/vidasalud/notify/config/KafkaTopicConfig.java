package com.vidasalud.notify.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los topics que usa notify. KafkaAdmin solo crea los que no existen, así notify
 * funciona aunque arranque antes que appointments o streams (el broker tiene la creación
 * automática de topics desactivada, como en la guía).
 *
 *  - vidasalud.citas / vidasalud.alertas : mismas particiones y retención que sus productores
 *  - *.DLT (dead letter topic)           : aquí van los mensajes que no se pudieron procesar;
 *                                          retención más larga (14 días) para poder revisarlos
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicProperties.class)
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic citasTopic(KafkaTopicProperties p) {
        return base(p.topic(), p)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")      // 7 días
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    @Bean
    public NewTopic alertasTopic(KafkaTopicProperties p) {
        return base(p.alertasTopic(), p)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")     // 30 días
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    @Bean
    public NewTopic citasDltTopic(KafkaTopicProperties p) {
        return dlt(p.topic() + ".DLT", p);
    }

    @Bean
    public NewTopic alertasDltTopic(KafkaTopicProperties p) {
        return dlt(p.alertasTopic() + ".DLT", p);
    }

    private static TopicBuilder base(String name, KafkaTopicProperties p) {
        return TopicBuilder.name(name)
                .partitions(p.partitions())
                .replicas(p.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(p.minInsyncReplicas()));
    }

    private static NewTopic dlt(String name, KafkaTopicProperties p) {
        return base(name, p)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(p.dltRetentionMs()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }
}
