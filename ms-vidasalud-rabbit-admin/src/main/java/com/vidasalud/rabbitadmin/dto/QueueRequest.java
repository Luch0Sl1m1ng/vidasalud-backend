package com.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/mq/queues. Solo "name" es obligatorio. */
public record QueueRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = NamePolicy.NAME_REGEX, message = "name inválido: " + NamePolicy.NAME_MESSAGE)
        String name,

        /** Por defecto true (cola durable). */
        Boolean durable,

        /** Por defecto false. */
        Boolean autoDelete,

        @Pattern(regexp = NamePolicy.OPTIONAL_NAME_REGEX, message = "deadLetterExchange inválido: " + NamePolicy.NAME_MESSAGE)
        String deadLetterExchange,

        @Pattern(regexp = NamePolicy.ROUTING_KEY_REGEX, message = "deadLetterRoutingKey inválida: " + NamePolicy.ROUTING_KEY_MESSAGE)
        String deadLetterRoutingKey,

        @Min(value = 1, message = "messageTtlMs debe ser >= 1")
        Integer messageTtlMs,

        @Min(value = 1, message = "maxLength debe ser >= 1")
        Integer maxLength) {
}
