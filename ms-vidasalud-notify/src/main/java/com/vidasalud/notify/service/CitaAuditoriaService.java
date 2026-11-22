package com.vidasalud.notify.service;

import com.vidasalud.notify.consumer.ProcessedEventStore;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lógica de negocio del flujo Kafka de citas: valida el evento y deja el registro de auditoría
 * del cambio de estado. NO conoce Kafka (ni topics, ni offsets): eso vive en consumer/ y config/.
 *
 *  - evento sin payload / sin id de cita -> NonRecoverableMessageException (va directo a la DLT)
 *  - evento repetido (mismo eventId)     -> se ignora (idempotencia: Kafka entrega "al menos una vez")
 *  - fallo simulado de demo              -> ver DemoFailureSimulator (reintentos y DLT)
 */
@Service
public class CitaAuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(CitaAuditoriaService.class);

    private final ProcessedEventStore store;
    private final DemoFailureSimulator failureSimulator;

    public CitaAuditoriaService(ProcessedEventStore store, DemoFailureSimulator failureSimulator) {
        this.store = store;
        this.failureSimulator = failureSimulator;
    }

    public void registrar(MessageEnvelope envelope) {
        if (envelope == null || envelope.payload() == null || envelope.payload().appointmentId() == null) {
            throw new NonRecoverableMessageException("evento de cita inválido: falta payload o appointmentId");
        }
        String clave = "kafka-citas:" + envelope.eventId();
        if (store.yaProcesado(clave)) {
            log.warn("[AUDITORIA] evento duplicado eventId={} -> se ignora", envelope.eventId());
            return;
        }

        failureSimulator.verificar(envelope);

        log.info("[AUDITORIA] cita={} paciente={} estado {} -> {}",
                envelope.payload().appointmentId(), envelope.payload().pacienteId(),
                envelope.payload().estadoAnterior(), envelope.payload().estadoNuevo());
        store.marcar(clave);
    }
}
