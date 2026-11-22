package com.vidasalud.notify.consumer;

import com.vidasalud.notify.dto.AlertaCitaDto;
import com.vidasalud.notify.service.AlertaPacienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR Kafka del SEGUNDO topic: las alertas que genera ms-vidasalud-streams.
 *
 * Usa su propio consumer group ("vidasalud-notify-alertas"), distinto al de las citas: cada grupo
 * lleva su propio offset, así que leer alertas no afecta a la lectura de citas (ni al revés).
 * Mismo manejo de errores que el consumidor de citas (reintentos y DLT "vidasalud.alertas.DLT").
 */
@Component
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class AlertasKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertasKafkaConsumer.class);

    private final AlertaPacienteService service;

    public AlertasKafkaConsumer(AlertaPacienteService service) {
        this.service = service;
    }

    @KafkaListener(
            topics = "${vidasalud.kafka.alertas-topic}",
            groupId = "${vidasalud.kafka.alertas-group-id}",
            properties = "spring.json.value.default.type=com.vidasalud.notify.dto.AlertaCitaDto")
    public void onMessage(
            @Payload AlertaCitaDto alerta,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        service.notificar(alerta);

        log.info("[KAFKA ALERTA RECIBIDA] partition={} offset={} key={} paciente={} severidad={}",
                partition, offset, key != null ? key : "(sin key)", alerta.pacienteId(), alerta.severidad());
    }
}
