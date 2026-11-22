package com.vidasalud.kafkaadmin.exception;

/** No hay conexión con los brokers (o respondieron fuera de tiempo). */
public class KafkaUnavailableException extends RuntimeException {
    public KafkaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
