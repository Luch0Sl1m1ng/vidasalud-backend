package com.vidasalud.appointments.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentKafkaPublisherTest {

    private static final String TOPIC = "vidasalud.citas";

    private KafkaTemplate<String, MessageEnvelope> kafkaTemplate;
    private AppointmentKafkaPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new AppointmentKafkaPublisher(kafkaTemplate,
                new KafkaTopicProperties(true, TOPIC, 3, (short) 1, 604_800_000L, "delete", 1));
    }

    private AppointmentEventPayload payload(long id, String anterior, String nuevo) {
        return new AppointmentEventPayload(id, "paciente-1", 3L, 2L, anterior, nuevo);
    }

    private CompletableFuture<SendResult<String, MessageEnvelope>> envioExitoso() {
        RecordMetadata md = new RecordMetadata(new TopicPartition(TOPIC, 1), 0L, 5, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(TOPIC, "k", (MessageEnvelope) null), md));
    }

    @Test
    void publicaEnElTopicConKeyDeLaCitaYElDtoEnvelope() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageEnvelope.class))).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(7L, "SOLICITADA", "CONFIRMADA"));

        ArgumentCaptor<MessageEnvelope> envelope = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-7"), envelope.capture());

        MessageEnvelope enviado = envelope.getValue();
        assertEquals("APPOINTMENT_STATE_CHANGED", enviado.type());
        assertEquals(7L, enviado.payload().appointmentId());
        assertEquals("CONFIRMADA", enviado.payload().estadoNuevo());
        // el envelope trae los campos comunes del caso
        assertNotNull(enviado.eventId());
        assertNotNull(enviado.traceId());
        assertEquals("appointment-7", enviado.correlationId());
    }

    @Test
    void todosLosEventosDeUnaMismaCitaUsanLaMismaKey() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageEnvelope.class))).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(9L, null, "SOLICITADA"));
        publisher.publicarCambio(payload(9L, "SOLICITADA", "CONFIRMADA"));
        publisher.publicarCambio(payload(9L, "CONFIRMADA", "EN_ESPERA"));

        // misma key -> Kafka calcula el mismo hash -> misma partición -> orden garantizado
        verify(kafkaTemplate, times(3)).send(eq(TOPIC), eq("cita-9"), any(MessageEnvelope.class));
    }

    @Test
    void citasDistintasUsanKeysDistintas() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageEnvelope.class))).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(1L, null, "SOLICITADA"));
        publisher.publicarCambio(payload(2L, null, "SOLICITADA"));

        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-1"), any(MessageEnvelope.class));
        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-2"), any(MessageEnvelope.class));
    }

    @Test
    void siKafkaLanzaExcepcionElNegocioNoSeRompe() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageEnvelope.class)))
                .thenThrow(new IllegalStateException("kafka caido"));

        assertDoesNotThrow(() -> publisher.publicarCambio(payload(7L, null, "SOLICITADA")));
    }

    @Test
    void siElEnvioAsincronoFallaElNegocioNoSeRompe() {
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageEnvelope.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        assertDoesNotThrow(() -> publisher.publicarCambio(payload(7L, null, "SOLICITADA")));
    }
}
