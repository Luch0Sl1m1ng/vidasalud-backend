package com.vidasalud.notify.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.consumer.ProcessedEventStore;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.exception.RecoverableMessageException;
import com.vidasalud.notify.model.AppointmentEventPayload;
import com.vidasalud.notify.model.MessageEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitaAuditoriaServiceTest {

    private CitaAuditoriaService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    private static MessageEnvelope evento(String eventId, Long citaId, String paciente) {
        return new MessageEnvelope("APPOINTMENT_STATE_CHANGED", eventId, Instant.now(), "t", "c",
                new AppointmentEventPayload(citaId, paciente, 3L, 2L, "SOLICITADA", "CONFIRMADA"));
    }

    @BeforeEach
    void setUp() {
        DemoFailureSimulator simulator = new DemoFailureSimulator(
                new NotifyProperties(true, new NotifyProperties.Retry(3, 1, 2.0, 10)));
        service = new CitaAuditoriaService(new ProcessedEventStore(), simulator);
        logger = (Logger) LoggerFactory.getLogger(CitaAuditoriaService.class);
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
    void registraUnEventoValido() {
        assertDoesNotThrow(() -> service.registrar(evento("e-1", 9L, "paciente-1")));

        assertEquals(1, logs.list.size());
        assertEquals(Level.INFO, logs.list.get(0).getLevel());
    }

    @Test
    void unEventoRepetidoSeIgnora() {
        service.registrar(evento("e-1", 9L, "paciente-1"));
        service.registrar(evento("e-1", 9L, "paciente-1"));

        assertEquals(2, logs.list.size());
        assertEquals(Level.WARN, logs.list.get(1).getLevel());
    }

    @Test
    void sinAppointmentIdEsUnErrorNoRecuperable() {
        assertThrows(NonRecoverableMessageException.class, () -> service.registrar(evento("e-2", null, "paciente-1")));
        assertThrows(NonRecoverableMessageException.class, () -> service.registrar(null));
    }

    @Test
    void elFalloSimuladoFatalNoSeReintenta() {
        assertThrows(NonRecoverableMessageException.class, () -> service.registrar(evento("e-3", 9L, "fail-fatal-1")));
    }

    @Test
    void elFalloSimuladoTemporalSePuedeReintentar() {
        MessageEnvelope e = evento("e-4", 9L, "fail-temp-1");

        assertThrows(RecoverableMessageException.class, () -> service.registrar(e));
        // no quedó marcado como procesado: el reintento vuelve a ejecutar la lógica
        assertThrows(RecoverableMessageException.class, () -> service.registrar(e));
    }
}
