package com.vidasalud.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Kafka (prefijo "vidasalud.kafka" en application.yml).
 * enabled=false (por defecto): los consumidores Kafka no se crean y notify funciona solo con RabbitMQ.
 *
 *  - topic / groupId               : eventos de citas (lo publica appointments) y su consumer group
 *  - alertasTopic / alertasGroupId : alertas (las publica ms-vidasalud-streams) y su consumer group
 *  - concurrency                   : hilos consumidores por listener (= nº de particiones)
 *  - retry                         : reintentos con backoff exponencial antes de mandar a la DLT
 */
@ConfigurationProperties(prefix = "vidasalud.kafka")
public record KafkaTopicProperties(
        boolean enabled,
        String topic,
        String groupId,
        String alertasTopic,
        String alertasGroupId,
        int partitions,
        short replicas,
        int minInsyncReplicas,
        int concurrency,
        long dltRetentionMs,
        Retry retry) {

    public record Retry(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {
    }
}
