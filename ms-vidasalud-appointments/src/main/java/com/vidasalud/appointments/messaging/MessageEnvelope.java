package com.vidasalud.appointments.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope común de todos los mensajes (buena práctica del caso):
 * type, eventId, timestamp, traceId, correlationId + payload.
 * El consumidor usa eventId para la idempotencia.
 */
public record MessageEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        AppointmentEventPayload payload) {

    public static MessageEnvelope nuevo(String type, AppointmentEventPayload payload) {
        return new MessageEnvelope(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                UUID.randomUUID().toString(),
                "appointment-" + payload.appointmentId(),
                payload);
    }
}
