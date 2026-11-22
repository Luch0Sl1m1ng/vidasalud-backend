package com.vidasalud.appointments.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Kafka (prefijo "vidasalud.kafka" en application.yml).
 *
 * enabled=false (por defecto) deja Kafka totalmente apagado: no se crea el topic,
 * no se publica nada y el servicio se comporta igual que antes (solo RabbitMQ).
 *
 * Parámetros de administración del topic de citas (EP5):
 *  - partitions / replicas      : paralelismo y tolerancia a fallos
 *  - retentionMs                : cuánto tiempo se conservan los eventos (7 días)
 *  - cleanupPolicy              : "delete" = se borran los eventos viejos (es un log de eventos)
 *  - minInsyncReplicas          : réplicas que deben confirmar cada escritura (acks=all)
 */
@ConfigurationProperties(prefix = "vidasalud.kafka")
public record KafkaTopicProperties(
        boolean enabled,
        String topic,
        int partitions,
        short replicas,
        long retentionMs,
        String cleanupPolicy,
        int minInsyncReplicas) {
}
