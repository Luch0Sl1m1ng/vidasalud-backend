package com.vidasalud.appointments.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentKafkaPublisherTest {

    private static final String TOPIC = "vidasalud.citas";

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private AppointmentKafkaPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new AppointmentKafkaPublisher(kafkaTemplate,
                new KafkaTopicProperties(true, TOPIC, 3, (short) 1), objectMapper);
    }

    private AppointmentEventPayload payload(long id, String anterior, String nuevo) {
        return new AppointmentEventPayload(id, "paciente-1", 3L, 2L, anterior, nuevo);
    }

    private CompletableFuture<SendResult<String, String>> envioExitoso() {
        RecordMetadata md = new RecordMetadata(new TopicPartition(TOPIC, 1), 0L, 5, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(TOPIC, "k", "v"), md));
    }

    @Test
    void publicaEnElTopicConKeyDeLaCitaYEnvelopeJson() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(7L, "SOLICITADA", "CONFIRMADA"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-7"), json.capture());

        JsonNode node = objectMapper.readTree(json.getValue());
        assertEquals("APPOINTMENT_STATE_CHANGED", node.get("type").asText());
        assertEquals(7L, node.get("payload").get("appointmentId").asLong());
        assertEquals("CONFIRMADA", node.get("payload").get("estadoNuevo").asText());
        // el envelope trae los campos comunes del caso
        assertEquals(true, node.hasNonNull("eventId"));
        assertEquals(true, node.hasNonNull("traceId"));
        assertEquals("appointment-7", node.get("correlationId").asText());
    }

    @Test
    void todosLosEventosDeUnaMismaCitaUsanLaMismaKey() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(9L, null, "SOLICITADA"));
        publisher.publicarCambio(payload(9L, "SOLICITADA", "CONFIRMADA"));
        publisher.publicarCambio(payload(9L, "CONFIRMADA", "EN_ESPERA"));

        // misma key -> Kafka calcula el mismo hash -> misma partición -> orden garantizado
        verify(kafkaTemplate, times(3)).send(eq(TOPIC), eq("cita-9"), anyString());
    }

    @Test
    void citasDistintasUsanKeysDistintas() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(envioExitoso());

        publisher.publicarCambio(payload(1L, null, "SOLICITADA"));
        publisher.publicarCambio(payload(2L, null, "SOLICITADA"));

        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-1"), anyString());
        verify(kafkaTemplate).send(eq(TOPIC), eq("cita-2"), anyString());
    }

    @Test
    void siKafkaLanzaExcepcionElNegocioNoSeRompe() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("kafka caido"));

        assertDoesNotThrow(() -> publisher.publicarCambio(payload(7L, null, "SOLICITADA")));
    }

    @Test
    void siElEnvioAsincronoFallaElNegocioNoSeRompe() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        assertDoesNotThrow(() -> publisher.publicarCambio(payload(7L, null, "SOLICITADA")));
    }
}
