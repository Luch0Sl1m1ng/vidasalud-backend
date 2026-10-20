package com.vidasalud.rabbitadmin.dto;

/** Regla única de nombres válidos para colas, exchanges y routing keys (evita inyecciones y nombres raros). */
public final class NamePolicy {

    public static final String NAME_REGEX = "^[A-Za-z0-9._-]{1,255}$";
    public static final String NAME_MESSAGE =
            "solo letras, números, punto, guion y guion bajo (1 a 255 caracteres)";

    /** Igual que NAME_REGEX pero permite vacío (campos opcionales). */
    public static final String OPTIONAL_NAME_REGEX = "^[A-Za-z0-9._-]{0,255}$";

    /** Las routing keys admiten además * y # (comodines del exchange topic). Vacía = permitida (fanout). */
    public static final String ROUTING_KEY_REGEX = "^[A-Za-z0-9._*#-]{0,255}$";
    public static final String ROUTING_KEY_MESSAGE =
            "solo letras, números, punto, guion, guion bajo, * y # (máximo 255 caracteres)";

    private NamePolicy() {
    }

    public static boolean esNombreValido(String nombre) {
        return nombre != null && nombre.matches(NAME_REGEX);
    }
}
