package com.vidasalud.streams.model;

/** Alerta de salida (equivale a la "AlertaFraude" de la guia). */
public record AlertaCita(
        String pacienteId,
        long cancelaciones,
        int umbral,
        String motivo,
        String severidad,
        String detectadoEn) {
}
