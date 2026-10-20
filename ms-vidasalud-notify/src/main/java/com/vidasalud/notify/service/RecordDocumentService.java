package com.vidasalud.notify.service;

import com.vidasalud.notify.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lógica del flujo "record": genera el comprobante o resumen de la atención.
 * La generación del PDF se simula con un log; para un PDF real se agrega
 * una librería (p. ej. OpenPDF) aquí, sin tocar consumidores ni configuración.
 */
@Service
public class RecordDocumentService {

    private static final Logger log = LoggerFactory.getLogger(RecordDocumentService.class);

    private final DemoFailureSimulator failureSimulator;

    public RecordDocumentService(DemoFailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void generar(MessageEnvelope envelope) {
        failureSimulator.verificar(envelope);

        String prefijo = "APPOINTMENT_SUMMARY".equals(envelope.type()) ? "resumen" : "comprobante";
        log.info("[PDF] generado {}-atencion-{}.pdf para paciente={}",
                prefijo, envelope.payload().appointmentId(), envelope.payload().pacienteId());
    }
}
