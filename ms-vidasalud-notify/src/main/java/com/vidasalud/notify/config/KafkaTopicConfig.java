package com.vidasalud.notify.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara el mismo topic que el productor (ms-vidasalud-appointments). KafkaAdmin solo lo
 * crea si no existe, así notify funciona aunque arranque antes que appointments
 * (el broker tiene la creación automática de topics desactivada, como en la guía).
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
