package com.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/mq/exchanges. */
public record ExchangeRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = NamePolicy.NAME_REGEX, message = "name inválido: " + NamePolicy.NAME_MESSAGE)
        String name,

        @NotBlank(message = "type es obligatorio")
        @Pattern(regexp = "^(direct|topic|fanout|headers)$",
                message = "type debe ser direct, topic, fanout o headers")
        String type,

        /** Por defecto true. */
        Boolean durable,

        /** Por defecto false. */
        Boolean autoDelete) {
}
