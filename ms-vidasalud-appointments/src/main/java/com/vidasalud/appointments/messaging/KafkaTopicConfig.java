package com.vidasalud.appointments.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Crea el topic de las citas al arrancar (equivale a "Crear el Topic con Particiones"
 * de la guía): 3 particiones y factor de replicación 1 (hay un solo broker).
 * KafkaAdmin lo crea solo si todavía no existe.
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
                .build();
    }
}
