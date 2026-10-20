package com.vidasalud.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.exception.RecoverableMessageException;
import com.vidasalud.notify.model.AppointmentEventPayload;
import com.vidasalud.notify.model.MessageEnvelope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReliableMessageProcessorTest {

    private static final long TAG = 42L;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private Channel channel;
    private SimpleMeterRegistry meters;
    private ReliableMessageProcessor processor;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        meters = new SimpleMeterRegistry();
        NotifyProperties props = new NotifyProperties(false, new NotifyProperties.Retry(3, 1, 1.0, 2));
        processor = new ReliableMessageProcessor(mapper, new ProcessedEventStore(), props, meters);
    }

    private Message mensaje(String eventId) throws Exception {
        MessageEnvelope env = new MessageEnvelope("APPOINTMENT_CONFIRMED", eventId, Instant.now(),
                "trace-1", "appointment-9",
                new AppointmentEventPayload(9L, "paciente-1", 3L, 2L, "SOLICITADA", "CONFIRMADA"));
        return conTag(mapper.writeValueAsBytes(env));
    }

    private Message conTag(byte[] body) {
        MessageProperties mp = new MessageProperties();
        mp.setDeliveryTag(TAG);
        return new Message(body, mp);
    }

    @Test
    void exitoHaceAck() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        processor.procesar("email", mensaje("e-1"), channel, env -> llamadas.incrementAndGet());

        assertEquals(1, llamadas.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    void jsonInvalidoVaADlqSinEjecutarElHandler() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();
        Message basura = conTag("esto no es json".getBytes(StandardCharsets.UTF_8));

        processor.procesar("email", basura, channel, env -> llamadas.incrementAndGet());

        assertEquals(0, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
        assertEquals(1.0, meters.counter("vidasalud.dlq.messages", "flow", "email").count());
    }

    @Test
    void errorNoRecuperableVaADlqSinReintentar() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        processor.procesar("record", mensaje("e-2"), channel, env -> {
            llamadas.incrementAndGet();
            throw new NonRecoverableMessageException("dato invalido");
        });

        assertEquals(1, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void errorRecuperablePersistenteReintentaYTerminaEnDlq() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        processor.procesar("admission", mensaje("e-3"), channel, env -> {
            llamadas.incrementAndGet();
            throw new RecoverableMessageException("servicio caido");
        });

        assertEquals(3, llamadas.get());   // max-attempts = 3
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void errorRecuperableQueSeSuperaTerminaEnAck() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        processor.procesar("email", mensaje("e-4"), channel, env -> {
            if (llamadas.incrementAndGet() < 2) {
                throw new RecoverableMessageException("timeout");
            }
        });

        assertEquals(2, llamadas.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    void eventoDuplicadoSeConfirmaSinReprocesar() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        processor.procesar("email", mensaje("e-5"), channel, env -> llamadas.incrementAndGet());
        processor.procesar("email", mensaje("e-5"), channel, env -> llamadas.incrementAndGet());

        assertEquals(1, llamadas.get());   // la segunda vez no ejecuta el handler
        verify(channel, org.mockito.Mockito.times(2)).basicAck(TAG, false);
    }
}
