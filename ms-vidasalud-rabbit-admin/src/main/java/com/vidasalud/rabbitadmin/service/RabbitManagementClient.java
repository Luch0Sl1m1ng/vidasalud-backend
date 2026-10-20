package com.vidasalud.rabbitadmin.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura (solo GET) a través de la API HTTP del Management UI.
 * AMQP no permite "listar" colas/exchanges, por eso este cliente existe.
 * Devuelve solo los campos útiles (la API completa trae cientos de datos).
 */
@Component
public class RabbitManagementClient {

    static final List<String> CAMPOS_COLA =
            List.of("name", "vhost", "durable", "auto_delete", "state", "messages", "consumers", "arguments");
    static final List<String> CAMPOS_EXCHANGE =
            List.of("name", "vhost", "type", "durable", "auto_delete");
    static final List<String> CAMPOS_BINDING =
            List.of("source", "vhost", "destination", "destination_type", "routing_key");

    private final RestClient restClient;

    public RabbitManagementClient(RestClient rabbitManagementRestClient) {
        this.restClient = rabbitManagementRestClient;
    }

    public List<Map<String, Object>> listarColas() {
        return proyectar(consultar("queues"), CAMPOS_COLA);
    }

    public List<Map<String, Object>> listarExchanges() {
        return proyectar(consultar("exchanges"), CAMPOS_EXCHANGE);
    }

    public List<Map<String, Object>> listarBindings() {
        return proyectar(consultar("bindings"), CAMPOS_BINDING);
    }

    private List<Map<String, Object>> consultar(String recurso) {
        return restClient.get()
                .uri("/api/{recurso}", recurso)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
    }

    /** Se queda solo con los campos pedidos, en ese orden. */
    static List<Map<String, Object>> proyectar(List<Map<String, Object>> filas, List<String> campos) {
        if (filas == null) {
            return List.of();
        }
        return filas.stream().map(fila -> {
            Map<String, Object> corta = new LinkedHashMap<>();
            for (String campo : campos) {
                if (fila.containsKey(campo)) {
                    corta.put(campo, fila.get(campo));
                }
            }
            return corta;
        }).toList();
    }
}
