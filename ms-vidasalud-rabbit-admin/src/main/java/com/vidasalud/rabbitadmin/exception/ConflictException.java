package com.vidasalud.rabbitadmin.exception;

/** El broker rechazó la operación (p. ej. la cola ya existe con otros argumentos o está en uso). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
