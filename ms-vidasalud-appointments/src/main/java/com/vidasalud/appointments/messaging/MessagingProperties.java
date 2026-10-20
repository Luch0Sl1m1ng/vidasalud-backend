package com.vidasalud.appointments.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topología RabbitMQ leídos desde application.yml
 * (prefijo "vidasalud.rabbit"). Es la ÚNICA fuente de nombres de
 * exchanges, colas y routing keys: ninguna otra clase los escribe a mano.
 */
@ConfigurationProperties(prefix = "vidasalud.rabbit")
public record MessagingProperties(
        Exchanges exchanges,
        Queues queues,
        RoutingKeys routingKeys,
        TopicPatterns topicPatterns,
        TopicRoutingKeys topicRoutingKeys,
        DlqKeys dlqKeys) {

    public record Exchanges(String direct, String topic, String dlx) {}

    public record Queues(String email, String emailDlq,
                         String admission, String admissionDlq,
                         String record, String recordDlq) {}

    /** Claves de binding del exchange direct (cmd.direct). */
    public record RoutingKeys(String email, String admission, String record) {}

    /** Patrones de binding del exchange topic (cmd.topic). */
    public record TopicPatterns(String email, String admission, String record) {}

    /** Claves concretas que el productor publica en el exchange topic. */
    public record TopicRoutingKeys(String emailCall, String admissionCall, String recordSummary) {}

    /** Claves con las que el DLX (cmd.dead.dlx) entrega a cada DLQ. */
    public record DlqKeys(String email, String admission, String record) {}
}
