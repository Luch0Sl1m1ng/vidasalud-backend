package com.vidasalud.notify.exception;

/** Error que NO se arregla reintentando (mensaje inválido, dato inexistente...). Va directo a la DLQ. */
public class NonRecoverableMessageException extends RuntimeException {
    public NonRecoverableMessageException(String message) {
        super(message);
    }
}
