package com.vidasalud.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidasalud.notify.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR Kafka (Guía 3.2.1). Escucha el topic de citas y muestra en el log, por cada
 * mensaje:
 *   - partition: en cuál de las 3 particiones llegó
 *   - offset:    posición secuencial dentro de esa partición
 *   - key:       "cita-{id}" (los eventos de una misma cita llegan siempre a la misma partición)
 *
 * El grupo de consumidores (vidasalud-notify) guarda el último offset leído: si notify se
 * reinicia NO vuelve a leer los mensajes anteriores, sigue desde donde quedó.
 * El consumidor es independiente de las colas RabbitMQ.
 */
@Component
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class CitasKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CitasKafkaConsumer.class);

    private final ObjectMapper objectMapper;

    public CitasKafkaConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${vidasalud.kafka.topic}", groupId = "${vidasalud.kafka.group-id}")
    public void onMessage(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        try {
            MessageEnvelope envelope = objectMapper.readValue(message, MessageEnvelope.class);
            log.info("[KAFKA RECIBIDO] partition={} offset={} key={} type={} eventId={} cita={} estado={}->{}",
                    partition, offset, key != null ? key : "(sin key)", envelope.type(), envelope.eventId(),
                    envelope.payload().appointmentId(),
                    envelope.payload().estadoAnterior(), envelope.payload().estadoNuevo());
        } catch (Exception ex) {
            // Un mensaje ilegible no debe detener al consumidor: se deja constancia y se sigue.
            log.warn("[KAFKA RECIBIDO] partition={} offset={} key={} con contenido no reconocido ({}): {}",
                    partition, offset, key != null ? key : "(sin key)", ex.getClass().getSimpleName(), message);
        }
    }
}
