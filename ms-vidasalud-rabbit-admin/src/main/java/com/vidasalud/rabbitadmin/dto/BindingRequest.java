package com.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.amqp.core.Binding.DestinationType;

/**
 * Cuerpo de POST y DELETE /api/mq/bindings:
 * une "exchange" con "destination" (una cola o un exchange) usando "routingKey".
 */
public record BindingRequest(
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = NamePolicy.NAME_REGEX, message = "exchange inválido: " + NamePolicy.NAME_MESSAGE)
        String exchange,

        @NotBlank(message = "destination es obligatorio")
        @Pattern(regexp = NamePolicy.NAME_REGEX, message = "destination inválido: " + NamePolicy.NAME_MESSAGE)
        String destination,

        @NotNull(message = "destinationType es obligatorio (QUEUE o EXCHANGE)")
        DestinationType destinationType,

        /** Puede ir vacía o ausente (p. ej. exchange fanout). Admite * y # para topic. */
        @Pattern(regexp = NamePolicy.ROUTING_KEY_REGEX, message = "routingKey inválida: " + NamePolicy.ROUTING_KEY_MESSAGE)
        String routingKey) {
}
