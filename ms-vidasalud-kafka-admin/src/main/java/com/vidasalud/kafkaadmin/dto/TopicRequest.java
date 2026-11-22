package com.vidasalud.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Crear un topic.
 *  - partitions: paralelismo (máx. de consumidores del mismo grupo que leen en paralelo)
 *  - replicationFactor: copias de cada partición (no puede superar el número de brokers)
 *  - configs: configuración del topic (retention.ms, cleanup.policy, min.insync.replicas, ...)
 */
public record TopicRequest(
        @NotBlank(message = "el nombre es obligatorio")
        @Pattern(regexp = NamePolicy.TOPIC_REGEX, message = NamePolicy.TOPIC_MESSAGE)
        String name,

        @Min(value = 1, message = "mínimo 1 partición")
        @Max(value = 100, message = "máximo 100 particiones")
        int partitions,

        @Min(value = 1, message = "mínimo 1 réplica")
        @Max(value = 10, message = "máximo 10 réplicas")
        short replicationFactor,

        Map<String, String> configs) {
}
