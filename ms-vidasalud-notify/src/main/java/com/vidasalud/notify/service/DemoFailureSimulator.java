package com.vidasalud.notify.service;

import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.exception.RecoverableMessageException;
import com.vidasalud.notify.model.MessageEnvelope;
import org.springframework.stereotype.Component;

/**
 * SOLO PARA LA DEMO: permite mostrar la DLQ sin romper nada a mano.
 * Si vidasalud.notify.demo-failures=true y el pacienteId de la cita empieza con:
 *   "fail-temp"  -> error recuperable (reintenta 3 veces y termina en la DLQ)
 *   "fail-fatal" -> error no recuperable (va directo a la DLQ)
 */
@Component
public class DemoFailureSimulator {

    private final boolean enabled;

    public DemoFailureSimulator(NotifyProperties props) {
        this.enabled = props.demoFailures();
    }

    public void verificar(MessageEnvelope envelope) {
        verificarPaciente(envelope.payload().pacienteId());
    }

    /** Misma regla pero a partir del pacienteId (la usan también los consumidores Kafka). */
    public void verificarPaciente(String paciente) {
        if (!enabled) {
            return;
        }
        if (paciente == null) {
            return;
        }
        if (paciente.startsWith("fail-fatal")) {
            throw new NonRecoverableMessageException("fallo simulado (fail-fatal) para paciente " + paciente);
        }
        if (paciente.startsWith("fail-temp")) {
            throw new RecoverableMessageException("fallo temporal simulado (fail-temp) para paciente " + paciente);
        }
    }
}
