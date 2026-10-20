package com.vidasalud.notify.service;

import com.vidasalud.notify.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lógica de negocio del flujo "email": solo sabe enviar el correo.
 * No conoce RabbitMQ (ni colas, ni ACK): eso vive en consumer/ y config/.
 * El envío real (SMTP / SendGrid) se simula con un log.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final DemoFailureSimulator failureSimulator;

    public EmailNotificationService(DemoFailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void enviar(MessageEnvelope envelope) {
        failureSimulator.verificar(envelope);

        String asunto = switch (envelope.type()) {
            case "APPOINTMENT_REQUESTED" -> "Recibimos tu solicitud de atención";
            case "APPOINTMENT_CONFIRMED" -> "Tu atención fue confirmada";
            case "BOX_CALL_EMAIL" -> "Te están llamando al box " + envelope.payload().boxId();
            case "APPOINTMENT_CLOSED" -> "Tu atención fue cerrada. ¡Gracias por venir!";
            case "APPOINTMENT_CANCELLED" -> "Tu atención fue cancelada";
            default -> "Actualización de tu atención";
        };

        log.info("[EMAIL] para paciente={} | asunto='{}' | cita={} | estado {} -> {}",
                envelope.payload().pacienteId(), asunto, envelope.payload().appointmentId(),
                envelope.payload().estadoAnterior(), envelope.payload().estadoNuevo());
    }
}
