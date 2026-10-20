package com.vidasalud.notify.consumer;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro de eventos ya procesados para IDEMPOTENCIA: si RabbitMQ entrega
 * dos veces el mismo eventId (at-least-once), la segunda se confirma sin
 * volver a ejecutar el efecto (no se envía dos veces el mismo email).
 *
 * Implementación en memoria acotada (últimos 10.000). Para producción real
 * se reemplaza por Redis o una tabla; la interfaz de uso no cambia.
 */
@Component
public class ProcessedEventStore {

    private static final int MAX_ENTRADAS = 10_000;

    private final Map<String, Boolean> vistos = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_ENTRADAS;
                }
            });

    public boolean yaProcesado(String clave) {
        return vistos.containsKey(clave);
    }

    public void marcar(String clave) {
        vistos.put(clave, Boolean.TRUE);
    }
}
