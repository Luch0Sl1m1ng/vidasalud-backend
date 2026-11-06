package com.vidasalud.appointments.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Kafka (prefijo "vidasalud.kafka" en application.yml).
 *
 * enabled=false (por defecto) deja Kafka totalmente apagado: no se crea el topic,
 * no se publica nada y el servicio se comporta igual que antes (solo RabbitMQ).
 */
@ConfigurationProperties(prefix = "vidasalud.kafka")
public record KafkaTopicProperties(boolean enabled, String topic, int partitions, short replicas) {
}
