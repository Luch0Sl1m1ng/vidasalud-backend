package com.vidasalud.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Nombres de topics y umbral del pipeline (todo viene de application.yml, prefijo vidasalud.streams). */
@ConfigurationProperties(prefix = "vidasalud.streams")
public record StreamsProperties(
        String inputTopic,
        String alertsTopic,
        String porPacienteTopic,
        int umbralCancelaciones,
        int partitions,
        short replicas) {
}
