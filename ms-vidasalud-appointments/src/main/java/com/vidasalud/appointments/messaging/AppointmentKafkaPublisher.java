package com.vidasalud.appointments.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * PRODUCTOR Kafka (Guía 3.2.1). Publica cada cambio de estado de una atención en el
 * topic de citas.
 *
 * KEY = "cita-{id}"  ->  Kafka calcula hash(key) y manda SIEMPRE los eventos de la misma
 * cita a la MISMA partición, así sus cambios de estado (SOLICITADA -> CONFIRMADA -> ...)
 * se consumen en orden. Citas distintas se reparten entre las particiones.
 *
 * Igual que con RabbitMQ: la mensajería NO puede romper el negocio. La publicación es
 * asíncrona y cualquier error solo queda en el log; la API responde igual.
 */
@Component
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class AppointmentKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppointmentKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties props;
    private final ObjectMapper objectMapper;

    public AppointmentKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     KafkaTopicProperties props,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public void publicarCambio(AppointmentEventPayload payload) {
        try {
            MessageEnvelope envelope = MessageEnvelope.nuevo("APPOINTMENT_STATE_CHANGED", payload);
            String key = "cita-" + payload.appointmentId();
            String json = objectMapper.writeValueAsString(envelope);

            kafkaTemplate.send(props.topic(), key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[KAFKA ERROR] no se pudo publicar key={} eventId={}. Causa: {}",
                            key, envelope.eventId(), ex.getMessage());
                    return;
                }
                RecordMetadata md = result.getRecordMetadata();
                log.info("[KAFKA PUBLICADO] topic={} partition={} offset={} key={} type={} eventId={}",
                        md.topic(), md.partition(), md.offset(), key, envelope.type(), envelope.eventId());
            });
        } catch (JsonProcessingException | RuntimeException ex) {
            log.error("[KAFKA ERROR] no se pudo publicar el evento de la atención {}. "
                            + "La atención quedó guardada igual. Causa: {}",
                    payload.appointmentId(), ex.getMessage());
        }
    }
}
