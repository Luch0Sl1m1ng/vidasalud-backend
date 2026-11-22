package com.vidasalud.notify.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.dto.AlertaCitaDto;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.service.AlertaPacienteService;
import com.vidasalud.notify.service.DemoFailureSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertasKafkaConsumerTest {

    private AlertasKafkaConsumer consumer;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        DemoFailureSimulator simulator = new DemoFailureSimulator(
                new NotifyProperties(false, new NotifyProperties.Retry(3, 1, 2.0, 10)));
        consumer = new AlertasKafkaConsumer(new AlertaPacienteService(simulator));
        logger = (Logger) LoggerFactory.getLogger(AlertasKafkaConsumer.class);
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
    void procesaLaAlertaYMuestraParticionOffsetYKey() {
        AlertaCitaDto alerta = new AlertaCitaDto("paciente-1", 3, 3, "Paciente con cancelaciones reiteradas (3)",
                "MEDIA", "2026-11-22T12:00:00Z");

        consumer.onMessage(alerta, 1, 4L, "paciente-1");

        assertEquals(1, logs.list.size());
        String linea = logs.list.get(0).getFormattedMessage();
        assertTrue(linea.contains("partition=1"), linea);
        assertTrue(linea.contains("offset=4"), linea);
        assertTrue(linea.contains("key=paciente-1"), linea);
        assertTrue(linea.contains("severidad=MEDIA"), linea);
    }

    @Test
    void unaAlertaSinPacienteSeRechazaComoNoRecuperable() {
        AlertaCitaDto sinPaciente = new AlertaCitaDto(" ", 3, 3, "x", "MEDIA", "2026-11-22T12:00:00Z");

        assertThrows(NonRecoverableMessageException.class, () -> consumer.onMessage(sinPaciente, 0, 0L, null));
    }
}
