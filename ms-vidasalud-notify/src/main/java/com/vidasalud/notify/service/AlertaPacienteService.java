package com.vidasalud.notify.service;

import com.vidasalud.notify.dto.AlertaCitaDto;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lógica de negocio del flujo de alertas: avisa a la coordinación cuando streams detecta un
 * paciente con cancelaciones reiteradas. El aviso real (correo / mensaje interno) se simula con un log.
 * No conoce Kafka.
 */
@Service
public class AlertaPacienteService {

    private static final Logger log = LoggerFactory.getLogger(AlertaPacienteService.class);

    private final DemoFailureSimulator failureSimulator;

    public AlertaPacienteService(DemoFailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void notificar(AlertaCitaDto alerta) {
        if (alerta == null || alerta.pacienteId() == null || alerta.pacienteId().isBlank()) {
            throw new NonRecoverableMessageException("alerta inválida: falta pacienteId");
        }
        failureSimulator.verificarPaciente(alerta.pacienteId());

        log.warn("[ALERTA COORDINACION] paciente={} severidad={} cancelaciones={} motivo='{}'",
                alerta.pacienteId(), alerta.severidad(), alerta.cancelaciones(), alerta.motivo());
    }
}
