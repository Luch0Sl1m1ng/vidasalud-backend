package com.vidasalud.kafkaadmin.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Qué configuraciones de un topic se pueden crear o modificar desde la API.
 * Solo las de administración habitual; configuraciones peligrosas (p. ej. unclean.leader.election.enable)
 * quedan fuera a propósito.
 */
public final class TopicConfigPolicy {

    /** Claves numéricas (long). retention.ms y retention.bytes admiten -1 = sin límite. */
    private static final Set<String> NUMERICAS = Set.of(
            "retention.ms", "retention.bytes", "segment.bytes", "segment.ms",
            "max.message.bytes", "min.insync.replicas", "delete.retention.ms");

    private static final Set<String> PERMITIDAS = Set.of(
            "retention.ms", "retention.bytes", "segment.bytes", "segment.ms", "max.message.bytes",
            "min.insync.replicas", "delete.retention.ms", "cleanup.policy", "compression.type",
            "min.cleanable.dirty.ratio");

    private static final Set<String> CLEANUP = Set.of("delete", "compact", "compact,delete", "delete,compact");
    private static final Set<String> COMPRESSION = Set.of("producer", "uncompressed", "gzip", "snappy", "lz4", "zstd");

    private TopicConfigPolicy() {
    }

    public static Set<String> clavesPermitidas() {
        return PERMITIDAS;
    }

    /** Devuelve una copia validada y sin espacios sobrantes; lanza IllegalArgumentException si algo no corresponde. */
    public static Map<String, String> validar(Map<String, String> configs) {
        Map<String, String> limpias = new LinkedHashMap<>();
        if (configs == null) {
            return limpias;
        }
        for (Map.Entry<String, String> e : configs.entrySet()) {
            String clave = e.getKey() == null ? "" : e.getKey().trim();
            String valor = e.getValue() == null ? "" : e.getValue().trim();
            if (!PERMITIDAS.contains(clave)) {
                throw new IllegalArgumentException("Configuración no permitida: '" + clave
                        + "'. Permitidas: " + new java.util.TreeSet<>(PERMITIDAS));
            }
            if (valor.isEmpty()) {
                throw new IllegalArgumentException("La configuración '" + clave + "' no puede estar vacía");
            }
            validarValor(clave, valor);
            limpias.put(clave, valor);
        }
        return limpias;
    }

    private static void validarValor(String clave, String valor) {
        if (NUMERICAS.contains(clave)) {
            long numero;
            try {
                numero = Long.parseLong(valor);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("'" + clave + "' debe ser un número entero");
            }
            boolean admiteMenosUno = clave.equals("retention.ms") || clave.equals("retention.bytes");
            if (numero < (admiteMenosUno ? -1 : 1) && !(clave.equals("delete.retention.ms") && numero == 0)) {
                throw new IllegalArgumentException("'" + clave + "' tiene un valor fuera de rango: " + numero);
            }
        } else if (clave.equals("cleanup.policy") && !CLEANUP.contains(valor)) {
            throw new IllegalArgumentException("cleanup.policy debe ser: delete, compact o compact,delete");
        } else if (clave.equals("compression.type") && !COMPRESSION.contains(valor)) {
            throw new IllegalArgumentException("compression.type debe ser uno de " + COMPRESSION);
        } else if (clave.equals("min.cleanable.dirty.ratio")) {
            try {
                double ratio = Double.parseDouble(valor);
                if (ratio < 0 || ratio > 1) {
                    throw new IllegalArgumentException("min.cleanable.dirty.ratio debe estar entre 0 y 1");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("min.cleanable.dirty.ratio debe ser un número entre 0 y 1");
            }
        }
    }
}
