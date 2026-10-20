package com.vidasalud.rabbitadmin.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitManagementClientTest {

    @Test
    void proyectarDejaSoloLosCamposPedidos() {
        List<Map<String, Object>> filas = List.of(
                Map.of("name", "q.demo", "vhost", "/", "messages", 4, "backing_queue_status", "mucho ruido"));

        List<Map<String, Object>> corta = RabbitManagementClient.proyectar(filas, RabbitManagementClient.CAMPOS_COLA);

        assertEquals(1, corta.size());
        assertEquals("q.demo", corta.get(0).get("name"));
        assertEquals(4, corta.get(0).get("messages"));
        assertFalse(corta.get(0).containsKey("backing_queue_status"));
    }

    @Test
    void proyectarConNullDevuelveListaVacia() {
        assertTrue(RabbitManagementClient.proyectar(null, RabbitManagementClient.CAMPOS_COLA).isEmpty());
    }
}
