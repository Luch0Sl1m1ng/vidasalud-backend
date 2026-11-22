package com.vidasalud.kafkaadmin.dto;

/** Regla única de nombres válidos de topics y grupos (la misma que usa Kafka). */
public final class NamePolicy {

    public static final String TOPIC_REGEX = "^[A-Za-z0-9._-]{1,249}$";
    public static final String TOPIC_MESSAGE = "solo letras, números, punto, guion y guion bajo (1 a 249 caracteres)";

    private NamePolicy() {
    }

    public static boolean esNombreValido(String nombre) {
        return nombre != null && nombre.matches(TOPIC_REGEX) && !nombre.equals(".") && !nombre.equals("..");
    }
}
