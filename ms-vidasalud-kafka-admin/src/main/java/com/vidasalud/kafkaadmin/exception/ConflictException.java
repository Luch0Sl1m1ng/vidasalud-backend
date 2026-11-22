package com.vidasalud.kafkaadmin.exception;

/** La operación choca con el estado actual (p. ej. el topic ya existe). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
