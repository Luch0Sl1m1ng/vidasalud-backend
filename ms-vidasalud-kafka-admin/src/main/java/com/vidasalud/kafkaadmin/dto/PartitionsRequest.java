package com.vidasalud.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Aumentar las particiones de un topic. Kafka solo permite subir, nunca bajar. */
public record PartitionsRequest(
        @Min(value = 2, message = "mínimo 2 particiones")
        @Max(value = 100, message = "máximo 100 particiones")
        int totalPartitions) {
}
