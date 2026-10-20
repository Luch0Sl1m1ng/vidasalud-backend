package com.vidasalud.appointments.messaging;

import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppointmentEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private AppointmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        MessagingProperties props = new MessagingProperties(
                new MessagingProperties.Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
                new MessagingProperties.Queues("q.cmd.email", "q.cmd.email.dlq",
                        "q.cmd.admission", "q.cmd.admission.dlq", "q.cmd.record", "q.cmd.record.dlq"),
                new MessagingProperties.RoutingKeys("email.send", "admission.ticket", "record.gen"),
                new MessagingProperties.TopicPatterns("email.*", "admission.#", "record.*"),
                new MessagingProperties.TopicRoutingKeys("email.call", "admission.box.call", "record.summary"),
                new MessagingProperties.DlqKeys("email.dlq", "admission.dlq", "record.dlq"));
        publisher = new AppointmentEventPublisher(rabbitTemplate, props);
    }

    private Appointment cita(EstadoAtencion estado) {
        Appointment a = new Appointment();
        a.setId(7L);
        a.setPacienteId("paciente-1");
        a.setServicioId(3L);
        a.setBoxId(2L);
        a.setEstado(estado);
        return a;
    }

    @Test
    void confirmadaPublicaEmailYComprobanteEnExchangeDirect() {
        publisher.publicarCambio(cita(EstadoAtencion.CONFIRMADA), EstadoAtencion.SOLICITADA);

        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("record.gen"),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
    }

    @Test
    void enEsperaPublicaTicketDeAdmision() {
        publisher.publicarCambio(cita(EstadoAtencion.EN_ESPERA), EstadoAtencion.CONFIRMADA);

        verify(rabbitTemplate, times(1)).convertAndSend(eq("cmd.direct"), eq("admission.ticket"),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
    }

    @Test
    void enAtencionUsaExchangeTopicConRoutingKeysConcretas() {
        publisher.publicarCambio(cita(EstadoAtencion.EN_ATENCION), EstadoAtencion.EN_ESPERA);

        verify(rabbitTemplate).convertAndSend(eq("cmd.topic"), eq("email.call"),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.topic"), eq("admission.box.call"),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate, never()).convertAndSend(eq("cmd.direct"), anyString(),
                any(MessageEnvelope.class), any(MessagePostProcessor.class));
    }

    @Test
    void siRabbitEstaCaidoElNegocioNoSeRompe() {
        doThrow(new AmqpConnectException(new IOException("broker caido")))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(),
                        any(MessageEnvelope.class), any(MessagePostProcessor.class));

        assertDoesNotThrow(() ->
                publisher.publicarCambio(cita(EstadoAtencion.CANCELADA), EstadoAtencion.SOLICITADA));
    }
}
