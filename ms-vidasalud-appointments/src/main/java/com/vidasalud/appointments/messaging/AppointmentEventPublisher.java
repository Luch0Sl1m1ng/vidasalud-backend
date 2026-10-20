package com.vidasalud.appointments.messaging;

import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * PRODUCTOR RabbitMQ. Traduce un cambio de estado de la atención en
 * comandos asíncronos (email / ticket de admisión / PDF).
 *
 * Regla de oro (EP3): la mensajería NO puede romper el negocio.
 * Si RabbitMQ está caído, la atención ya quedó guardada en la BD y
 * solo se registra el error en el log; la API responde igual.
 */
@Component
public class AppointmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public AppointmentEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /**
     * @param anterior estado previo (null cuando la atención recién se crea)
     */
    public void publicarCambio(Appointment appointment, EstadoAtencion anterior) {
        AppointmentEventPayload payload = new AppointmentEventPayload(
                appointment.getId(),
                appointment.getPacienteId(),
                appointment.getServicioId(),
                appointment.getBoxId(),
                anterior == null ? null : anterior.name(),
                appointment.getEstado().name());

        String direct = props.exchanges().direct();
        String topic = props.exchanges().topic();

        try {
            switch (appointment.getEstado()) {
                case SOLICITADA -> enviar(direct, props.routingKeys().email(), "APPOINTMENT_REQUESTED", payload);
                case CONFIRMADA -> {
                    enviar(direct, props.routingKeys().email(), "APPOINTMENT_CONFIRMED", payload);
                    enviar(direct, props.routingKeys().record(), "APPOINTMENT_RECEIPT", payload);
                }
                case EN_ESPERA -> enviar(direct, props.routingKeys().admission(), "ADMISSION_TICKET", payload);
                case EN_ATENCION -> {
                    enviar(topic, props.topicRoutingKeys().emailCall(), "BOX_CALL_EMAIL", payload);
                    enviar(topic, props.topicRoutingKeys().admissionCall(), "BOX_CALL_TICKET", payload);
                }
                case CERRADA -> {
                    enviar(direct, props.routingKeys().email(), "APPOINTMENT_CLOSED", payload);
                    enviar(topic, props.topicRoutingKeys().recordSummary(), "APPOINTMENT_SUMMARY", payload);
                }
                case CANCELADA -> enviar(direct, props.routingKeys().email(), "APPOINTMENT_CANCELLED", payload);
            }
        } catch (RuntimeException ex) {
            log.error("No se pudo publicar el evento de la atención {} (estado {}). "
                            + "La atención quedó guardada igual. Causa: {}",
                    appointment.getId(), appointment.getEstado(), ex.getMessage());
        }
    }

    private void enviar(String exchange, String routingKey, String type, AppointmentEventPayload payload) {
        MessageEnvelope envelope = MessageEnvelope.nuevo(type, payload);

        rabbitTemplate.convertAndSend(exchange, routingKey, envelope, message -> {
            message.getMessageProperties().setMessageId(envelope.eventId());
            message.getMessageProperties().setCorrelationId(envelope.correlationId());
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader("x-trace-id", envelope.traceId());
            return message;
        });

        log.info("[PUBLICADO] exchange={} routingKey={} type={} eventId={} cita={}",
                exchange, routingKey, type, envelope.eventId(), payload.appointmentId());
    }
}
