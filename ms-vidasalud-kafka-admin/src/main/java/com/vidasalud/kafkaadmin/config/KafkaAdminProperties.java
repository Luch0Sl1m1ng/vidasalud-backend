package com.vidasalud.kafkaadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Parámetros del administrador (prefijo "vidasalud.kafka-admin"). */
@ConfigurationProperties(prefix = "vidasalud.kafka-admin")
public record KafkaAdminProperties(int timeoutSeconds, List<String> protectedTopics) {

    public KafkaAdminProperties {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
        protectedTopics = protectedTopics == null ? List.of() : List.copyOf(protectedTopics);
    }
}
