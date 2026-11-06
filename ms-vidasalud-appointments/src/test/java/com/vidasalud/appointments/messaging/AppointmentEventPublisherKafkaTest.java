package com.vidasalud.appointments.messaging;

import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Verifica que Kafka se publica además de RabbitMQ, y que los dos son independientes. */
class AppointmentEventPublisherKafkaTest {

    private RabbitTemplate rabbitTemplate;
    private AppointmentKafkaPublisher kafkaPublisher;
    private AppointmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        kafkaPublisher = mock(AppointmentKafkaPublisher.class);
        MessagingProperties props = new MessagingProperties(
                new MessagingProperties.Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
                new MessagingProperties.Queues("q.cmd.email", "q.cmd.email.dlq",
                        "q.cmd.admission", "q.cmd.admission.dlq", "q.cmd.record", "q.cmd.record.dlq"),
                new MessagingProperties.RoutingKeys("email.send", "admission.ticket", "record.gen"),
                new MessagingProperties.TopicPatterns("email.*", "admission.#", "record.*"),
                new MessagingProperties.TopicRoutingKeys("email.call", "admission.box.call", "record.summary"),
                new MessagingProperties.DlqKeys("email.dlq", "admission.dlq", "record.dlq"));
        publisher = new AppointmentEventPublisher(rabbitTemplate, props, kafkaPublisher);
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
    void cadaCambioDeEstadoSePublicaUnaVezEnKafka() {
        publisher.publicarCambio(cita(EstadoAtencion.CONFIRMADA), EstadoAtencion.SOLICITADA);

        ArgumentCaptor<AppointmentEventPayload> captor = ArgumentCaptor.forClass(AppointmentEventPayload.class);
        verify(kafkaPublisher).publicarCambio(captor.capture());
        assertEquals(7L, captor.getValue().appointmentId());
        assertEquals("SOLICITADA", captor.getValue().estadoAnterior());
        assertEquals("CONFIRMADA", captor.getValue().estadoNuevo());
    }

    @Test
    void siRabbitEstaCaidoKafkaIgualPublica() {
        doThrow(new AmqpConnectException(new IOException("broker caido")))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(MessageEnvelope.class), any(MessagePostProcessor.class));

        publisher.publicarCambio(cita(EstadoAtencion.CANCELADA), EstadoAtencion.SOLICITADA);

        verify(kafkaPublisher).publicarCambio(any(AppointmentEventPayload.class));
    }

    @Test
    void sinKafkaHabilitadoSoloFuncionaRabbit() {
        AppointmentEventPublisher soloRabbit = new AppointmentEventPublisher(rabbitTemplate,
                new MessagingProperties(
                        new MessagingProperties.Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
                        new MessagingProperties.Queues("a", "b", "c", "d", "e", "f"),
                        new MessagingProperties.RoutingKeys("email.send", "admission.ticket", "record.gen"),
                        new MessagingProperties.TopicPatterns("email.*", "admission.#", "record.*"),
                        new MessagingProperties.TopicRoutingKeys("email.call", "admission.box.call", "record.summary"),
                        new MessagingProperties.DlqKeys("email.dlq", "admission.dlq", "record.dlq")));

        soloRabbit.publicarCambio(cita(EstadoAtencion.CONFIRMADA), EstadoAtencion.SOLICITADA);

        verify(kafkaPublisher, never()).publicarCambio(any(AppointmentEventPayload.class));
    }
}
