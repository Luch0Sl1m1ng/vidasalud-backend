package com.vidasalud.notify.service;

import com.vidasalud.notify.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lógica del flujo "admission": emite el ticket de admisión / llamado a box (simulado con log). */
@Service
public class AdmissionTicketService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionTicketService.class);

    private final DemoFailureSimulator failureSimulator;

    public AdmissionTicketService(DemoFailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void emitir(MessageEnvelope envelope) {
        failureSimulator.verificar(envelope);

        log.info("[TICKET] ticket de admisión N°{} | tipo={} | paciente={} | box={}",
                envelope.payload().appointmentId(), envelope.type(),
                envelope.payload().pacienteId(), envelope.payload().boxId());
    }
}
