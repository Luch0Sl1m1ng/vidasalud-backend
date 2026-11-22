package com.vidasalud.kafkaadmin.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopicConfigPolicyTest {

    @Test
    void aceptaLasConfiguracionesDeAdministracion() {
        Map<String, String> ok = TopicConfigPolicy.validar(Map.of(
                "retention.ms", "604800000",
                "cleanup.policy", "compact",
                "min.insync.replicas", "2"));

        assertEquals(3, ok.size());
        assertEquals("compact", ok.get("cleanup.policy"));
    }

    @Test
    void retencionAdmiteMenosUnoParaSinLimite() {
        assertEquals("-1", TopicConfigPolicy.validar(Map.of("retention.ms", "-1")).get("retention.ms"));
    }

    @Test
    void rechazaConfiguracionesNoPermitidas() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicConfigPolicy.validar(Map.of("unclean.leader.election.enable", "true")));
    }

    @Test
    void rechazaValoresInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicConfigPolicy.validar(Map.of("cleanup.policy", "borrar")));
        assertThrows(IllegalArgumentException.class,
                () -> TopicConfigPolicy.validar(Map.of("retention.ms", "una semana")));
        assertThrows(IllegalArgumentException.class,
                () -> TopicConfigPolicy.validar(Map.of("min.insync.replicas", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> TopicConfigPolicy.validar(Map.of("retention.ms", " ")));
    }

    @Test
    void sinConfiguracionesDevuelveVacio() {
        assertEquals(0, TopicConfigPolicy.validar(null).size());
    }
}
