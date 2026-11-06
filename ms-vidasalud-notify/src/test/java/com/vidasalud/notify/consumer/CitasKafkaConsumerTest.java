package com.vidasalud.notify.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitasKafkaConsumerTest {

    private static final String JSON = """
            {"type":"APPOINTMENT_STATE_CHANGED","eventId":"e-1","timestamp":"2026-11-05T20:00:00Z",
             "traceId":"t-1","correlationId":"appointment-9",
             "payload":{"appointmentId":9,"pacienteId":"paciente-1","servicioId":3,"boxId":2,
                        "estadoAnterior":"SOLICITADA","estadoNuevo":"CONFIRMADA"}}
            """;

    private CitasKafkaConsumer consumer;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        consumer = new CitasKafkaConsumer(new ObjectMapper().findAndRegisterModules());
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
        consumer.onMessage(JSON, 2, 7L, "cita-9");

        assertEquals(1, logs.list.size());
        String linea = logs.list.get(0).getFormattedMessage();
        assertTrue(linea.contains("partition=2"), linea);
        assertTrue(linea.contains("offset=7"), linea);
        assertTrue(linea.contains("key=cita-9"), linea);
        assertTrue(linea.contains("estado=SOLICITADA->CONFIRMADA"), linea);
    }

    @Test
    void aceptaMensajesSinKey() {
        consumer.onMessage(JSON, 0, 0L, null);

        assertTrue(logs.list.get(0).getFormattedMessage().contains("key=(sin key)"));
    }

    @Test
    void unMensajeIlegibleNoDetieneAlConsumidor() {
        assertDoesNotThrow(() -> consumer.onMessage("esto no es json", 1, 3L, "cita-1"));

        assertEquals(Level.WARN, logs.list.get(0).getLevel());
    }
}
