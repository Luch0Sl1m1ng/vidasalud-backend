package com.vidasalud.notify.exception;

/** Error transitorio (servicio externo caído, timeout...). Se reintenta con backoff y, si se agota, va a la DLQ. */
public class RecoverableMessageException extends RuntimeException {
    public RecoverableMessageException(String message) {
        super(message);
    }
}
