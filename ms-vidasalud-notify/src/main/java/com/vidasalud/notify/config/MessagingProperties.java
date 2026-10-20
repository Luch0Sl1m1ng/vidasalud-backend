package com.vidasalud.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topología RabbitMQ leídos desde application.yml
 * (prefijo "vidasalud.rabbit"). Misma topología que declara el productor
 * (ms-vidasalud-appointments): ambos lados declaran lo mismo y RabbitMQ
 * lo acepta porque los argumentos son idénticos.
 */
@ConfigurationProperties(prefix = "vidasalud.rabbit")
public record MessagingProperties(
        Exchanges exchanges,
        Queues queues,
        RoutingKeys routingKeys,
        TopicPatterns topicPatterns,
        DlqKeys dlqKeys) {

    public record Exchanges(String direct, String topic, String dlx) {}

    public record Queues(String email, String emailDlq,
                         String admission, String admissionDlq,
                         String record, String recordDlq) {}

    public record RoutingKeys(String email, String admission, String record) {}

    public record TopicPatterns(String email, String admission, String record) {}

    public record DlqKeys(String email, String admission, String record) {}
}
