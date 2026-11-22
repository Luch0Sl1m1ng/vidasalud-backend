package com.vidasalud.streams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de topics, umbral y administración de topics del pipeline (prefijo vidasalud.streams).
 *
 *  - alertsRetentionMs : las alertas se conservan 30 días (cleanup.policy=delete)
 *  - minInsyncReplicas : réplicas que deben confirmar cada escritura
 *  - el topic por-paciente usa cleanup.policy=compact: guarda solo el ÚLTIMO total de cada paciente
 */
@ConfigurationProperties(prefix = "vidasalud.streams")
public record StreamsProperties(
        String inputTopic,
        String alertsTopic,
        String porPacienteTopic,
        int umbralCancelaciones,
        int partitions,
        short replicas,
        long alertsRetentionMs,
        int minInsyncReplicas) {
}
