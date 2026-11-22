package com.vidasalud.appointments.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración COMPLETA del cliente Kafka (productor), en un solo lugar.
 *
 *  - bootstrap.servers : viene de spring.kafka.bootstrap-servers (uno o varios brokers separados por coma)
 *  - key.serializer    : StringSerializer (key = "cita-{id}")
 *  - value.serializer  : JsonSerializer sobre el DTO MessageEnvelope (sin cabecera de tipo Java,
 *                        así cualquier consumidor, también Kafka Streams, puede leer el JSON)
 *  - acks=all + idempotencia : la escritura se confirma en todas las réplicas sincronizadas y un
 *                        reintento nunca duplica el mensaje
 *  - timeouts cortos   : si Kafka está caído el negocio NO se bloquea (solo queda en el log)
 */
@Configuration
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, MessageEnvelope> citasProducerFactory(KafkaProperties kafkaProperties,
                                                                         ObjectMapper objectMapper) {
        Map<String, Object> cfg = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.RETRIES_CONFIG, 3);
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        cfg.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        cfg.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        cfg.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 6000);

        // El JsonSerializer se configura SOLO con el setter (no también por propiedades): Spring Kafka no admite ambas.
        JsonSerializer<MessageEnvelope> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(cfg, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, MessageEnvelope> citasKafkaTemplate(
            ProducerFactory<String, MessageEnvelope> citasProducerFactory) {
        return new KafkaTemplate<>(citasProducerFactory);
    }
}
