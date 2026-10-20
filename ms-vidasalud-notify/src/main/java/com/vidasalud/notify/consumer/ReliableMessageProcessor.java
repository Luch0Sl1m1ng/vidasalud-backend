package com.vidasalud.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.vidasalud.notify.config.NotifyProperties;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import com.vidasalud.notify.model.MessageEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Lógica de CONFIRMACIÓN (ACK/NACK) compartida por todos los consumidores.
 *
 *  - JSON inválido / error no recuperable  -> NACK sin reencolar  -> DLQ (+ log + métrica)
 *  - Error recuperable o inesperado        -> reintenta con backoff exponencial;
 *                                             si se agotan los intentos -> NACK -> DLQ
 *  - Evento duplicado (mismo eventId)      -> ACK sin ejecutar nada (idempotencia)
 *  - Éxito                                 -> ACK
 *
 * El NACK con requeue=false hace que RabbitMQ envíe el mensaje al DLX
 * configurado en la cola (x-dead-letter-exchange), que lo deja en la DLQ.
 */
@Component
public class ReliableMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReliableMessageProcessor.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventStore store;
    private final NotifyProperties.Retry retry;
    private final MeterRegistry meterRegistry;

    public ReliableMessageProcessor(ObjectMapper objectMapper, ProcessedEventStore store,
                                    NotifyProperties props, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.retry = props.retry();
        this.meterRegistry = meterRegistry;
    }

    public void procesar(String flujo, Message message, Channel channel,
                         Consumer<MessageEnvelope> handler) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();

        MessageEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), MessageEnvelope.class);
        } catch (IOException ex) {
            rechazarADlq(flujo, tag, channel, "mensaje ilegible (JSON inválido): " + ex.getMessage(), null);
            return;
        }
        if (envelope == null || envelope.eventId() == null || envelope.type() == null || envelope.payload() == null) {
            rechazarADlq(flujo, tag, channel, "envelope incompleto (falta eventId, type o payload)", envelope);
            return;
        }

        String clave = flujo + ":" + envelope.eventId();
        if (store.yaProcesado(clave)) {
            log.warn("[{}] evento duplicado eventId={} -> se confirma sin reprocesar", flujo, envelope.eventId());
            channel.basicAck(tag, false);
            return;
        }

        long espera = retry.initialIntervalMs();
        int intento = 0;
        while (true) {
            intento++;
            try {
                handler.accept(envelope);
                store.marcar(clave);
                channel.basicAck(tag, false);
                log.info("[{}] ACK eventId={} type={} (intento {})", flujo, envelope.eventId(), envelope.type(), intento);
                return;
            } catch (NonRecoverableMessageException ex) {
                rechazarADlq(flujo, tag, channel, "error NO recuperable: " + ex.getMessage(), envelope);
                return;
            } catch (RuntimeException ex) {   // incluye RecoverableMessageException y errores inesperados
                if (intento >= retry.maxAttempts()) {
                    rechazarADlq(flujo, tag, channel,
                            "reintentos agotados (" + intento + "): " + ex.getMessage(), envelope);
                    return;
                }
                log.warn("[{}] fallo temporal eventId={} intento {}/{}: {}. Reintento en {} ms",
                        flujo, envelope.eventId(), intento, retry.maxAttempts(), ex.getMessage(), espera);
                if (!dormir(espera)) {
                    channel.basicNack(tag, false, true);   // la app se está apagando: vuelve a la cola
                    return;
                }
                espera = Math.min((long) (espera * retry.multiplier()), retry.maxIntervalMs());
            }
        }
    }

    private void rechazarADlq(String flujo, long tag, Channel channel, String motivo,
                              MessageEnvelope envelope) throws IOException {
        log.error("[{}] -> DLQ | motivo: {} | eventId={} type={} cita={}",
                flujo, motivo,
                envelope == null ? "?" : envelope.eventId(),
                envelope == null ? "?" : envelope.type(),
                envelope == null || envelope.payload() == null ? "?" : envelope.payload().appointmentId());
        meterRegistry.counter("vidasalud.dlq.messages", "flow", flujo).increment();
        channel.basicNack(tag, false, false);   // requeue=false => el DLX lo manda a la DLQ
    }

    private boolean dormir(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
