package com.vidasalud.kafkaadmin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/** Modificar configuraciones de un topic existente (solo claves permitidas, ver TopicConfigPolicy). */
public record TopicConfigRequest(
        @NotEmpty(message = "indica al menos una configuración")
        Map<String, String> configs) {
}
