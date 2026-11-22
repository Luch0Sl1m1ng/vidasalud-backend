package com.vidasalud.notify.consumer;

import com.vidasalud.notify.model.MessageEnvelope;
import com.vidasalud.notify.service.CitaAuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR Kafka de citas. Solo recibe el mensaje ya convertido a DTO (MessageEnvelope) y lo
 * entrega a CitaAuditoriaService: aquí NO hay lógica de negocio, solo mensajería.
 *
 *  - consumer group "vidasalud-notify": las 3 particiones se reparten entre los hilos del grupo; si
 *    notify se reinicia el grupo sigue desde el último offset confirmado (no relee todo).
 *  - Si el servicio lanza una excepción, el DefaultErrorHandler (config/KafkaConsumerConfig) decide:
 *    reintentar con backoff o mandar el mensaje a "vidasalud.citas.DLT".
 *  - Es independiente de las colas RabbitMQ.
 */
@Component
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class CitasKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CitasKafkaConsumer.class);

    private final CitaAuditoriaService service;

    public CitasKafkaConsumer(CitaAuditoriaService service) {
        this.service = service;
    }

    @KafkaListener(
            topics = "${vidasalud.kafka.topic}",
            groupId = "${vidasalud.kafka.group-id}",
            properties = "spring.json.value.default.type=com.vidasalud.notify.model.MessageEnvelope")
    public void onMessage(
            @Payload MessageEnvelope envelope,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        service.registrar(envelope);

        log.info("[KAFKA RECIBIDO] partition={} offset={} key={} type={} eventId={} cita={} estado={}->{}",
                partition, offset, key != null ? key : "(sin key)", envelope.type(), envelope.eventId(),
                envelope.payload().appointmentId(),
                envelope.payload().estadoAnterior(), envelope.payload().estadoNuevo());
    }
}
