package com.vidasalud.kafkaadmin.exception;

/** El broker rechazó la operación por una razón que no encaja en los otros errores. */
public class KafkaOperationException extends RuntimeException {
    public KafkaOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
