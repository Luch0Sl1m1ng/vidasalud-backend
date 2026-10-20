package com.vidasalud.notify.model;

public record AppointmentEventPayload(
        Long appointmentId,
        String pacienteId,
        Long servicioId,
        Long boxId,
        String estadoAnterior,
        String estadoNuevo) {
}
