package com.vidasalud.notify.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.model.AppointmentEventPayload;
import com.vidasalud.notify.model.MessageEnvelope;
import com.vidasalud.notify.service.CitaAuditoriaService;
import com.vidasalud.notify.service.DemoFailureSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitasKafkaConsumerTest {

    private static final MessageEnvelope EVENTO = new MessageEnvelope(
            "APPOINTMENT_STATE_CHANGED", "e-1", Instant.parse("2026-11-05T20:00:00Z"), "t-1", "appointment-9",
            new AppointmentEventPayload(9L, "paciente-1", 3L, 2L, "SOLICITADA", "CONFIRMADA"));

    private CitasKafkaConsumer consumer;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        DemoFailureSimulator simulator = new DemoFailureSimulator(
                new NotifyProperties(false, new NotifyProperties.Retry(3, 1, 2.0, 10)));
        consumer = new CitasKafkaConsumer(new CitaAuditoriaService(new ProcessedEventStore(), simulator));
        logger = (Logger) LoggerFactory.getLogger(CitasKafkaConsumer.class);
        logger.setLevel(Level.INFO);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void muestraParticionOffsetYKeyDelMensaje() {
        consumer.onMessage(EVENTO, 2, 7L, "cita-9");

        assertEquals(1, logs.list.size());
        String linea = logs.list.get(0).getFormattedMessage();
        assertTrue(linea.contains("partition=2"), linea);
        assertTrue(linea.contains("offset=7"), linea);
        assertTrue(linea.contains("key=cita-9"), linea);
        assertTrue(linea.contains("estado=SOLICITADA->CONFIRMADA"), linea);
    }

    @Test
    void aceptaMensajesSinKey() {
        consumer.onMessage(EVENTO, 0, 0L, null);

        assertTrue(logs.list.get(0).getFormattedMessage().contains("key=(sin key)"));
    }

    @Test
    void unEventoInvalidoSeRechazaComoNoRecuperable() {
        // el DefaultErrorHandler lo manda directo a la DLT (sin reintentos)
        assertThrows(NonRecoverableMessageException.class, () -> consumer.onMessage(null, 1, 3L, "cita-1"));
        assertTrue(logs.list.isEmpty());
    }
}
