package com.vidasalud.notify.model;

import java.time.Instant;

/** Mismo envelope común que publica ms-vidasalud-appointments. */
public record MessageEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        AppointmentEventPayload payload) {
}
