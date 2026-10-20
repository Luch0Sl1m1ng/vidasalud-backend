package com.vidasalud.appointments.messaging;

/** Datos de la atención que viajan dentro del envelope. Estados como String para desacoplar al consumidor. */
public record AppointmentEventPayload(
        Long appointmentId,
        String pacienteId,
        Long servicioId,
        Long boxId,
        String estadoAnterior,
        String estadoNuevo) {
}
