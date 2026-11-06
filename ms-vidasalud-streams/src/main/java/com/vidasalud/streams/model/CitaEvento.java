package com.vidasalud.streams.model;

/** Evento de cambio de estado de una atencion, ya limpio y enriquecido (equivale a la "Transaccion" de la guia). */
public record CitaEvento(
        String eventId,
        Long appointmentId,
        String pacienteId,
        String estadoAnterior,
        String estadoNuevo,
        String categoria) {
}
