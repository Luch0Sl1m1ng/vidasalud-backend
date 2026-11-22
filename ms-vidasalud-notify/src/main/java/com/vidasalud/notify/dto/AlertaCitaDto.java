package com.vidasalud.notify.dto;

/**
 * DTO de la alerta que publica ms-vidasalud-streams en el topic de alertas
 * (paciente con cancelaciones reiteradas). Es el contrato JSON entre ambos servicios.
 */
public record AlertaCitaDto(
        String pacienteId,
        long cancelaciones,
        int umbral,
        String motivo,
        String severidad,
        String detectadoEn) {
}
