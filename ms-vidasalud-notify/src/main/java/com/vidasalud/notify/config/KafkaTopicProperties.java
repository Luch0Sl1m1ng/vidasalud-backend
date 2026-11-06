package com.vidasalud.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Kafka (prefijo "vidasalud.kafka" en application.yml).
 * enabled=false (por defecto): el consumidor Kafka no se crea y notify funciona solo con RabbitMQ.
 */
@ConfigurationProperties(prefix = "vidasalud.kafka")
public record KafkaTopicProperties(boolean enabled, String topic, String groupId, int partitions, short replicas) {
}
