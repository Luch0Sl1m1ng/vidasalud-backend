package com.vidasalud.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidasalud.notify.exception.NonRecoverableMessageException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración COMPLETA del cliente Kafka (consumidor + manejo de errores), en un solo lugar.
 *
 *  CONSUMIDOR
 *   - bootstrap.servers : spring.kafka.bootstrap-servers (uno o varios brokers)
 *   - key.deserializer  : StringDeserializer
 *   - value.deserializer: ErrorHandlingDeserializer + JsonDeserializer -> el mensaje llega como DTO
 *                         (MessageEnvelope o AlertaCitaDto; el tipo lo fija cada @KafkaListener).
 *                         Si el JSON es inválido NO se rompe el consumidor: el error se encapsula
 *                         y lo trata el DefaultErrorHandler.
 *   - consumer groups   : uno por listener (ver @KafkaListener); las 3 particiones se reparten
 *                         entre los hilos del grupo (concurrency)
 *   - offsets           : el contenedor confirma (commit) el offset DESPUÉS de procesar el lote
 *                         (enable.auto.commit=false) -> procesamiento "al menos una vez"
 *
 *  ERRORES (DefaultErrorHandler)
 *   - error recuperable (RecoverableMessageException, timeouts...) -> reintentos con backoff exponencial
 *   - error NO recuperable (NonRecoverableMessageException, JSON inválido) -> directo a la DLT, sin reintentos
 *   - al agotar los reintentos el mensaje se publica en "<topic>.DLT" (misma partición) con las cabeceras
 *     de la excepción, y se deja constancia en el log. El consumidor sigue con el siguiente mensaje.
 */
@Configuration
@ConditionalOnProperty(name = "vidasalud.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConsumerFactory<String, Object> kafkaConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> cfg = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.vidasalud.notify.dto,com.vidasalud.notify.model");
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> kafkaConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler,
            KafkaTopicProperties props) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setConcurrency(props.concurrency());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(@Qualifier("dltKafkaTemplate") KafkaTemplate<String, Object> dltTemplate,
                                                 KafkaTopicProperties props) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        KafkaTopicProperties.Retry retry = props.retry();
        // maxAttempts = intentos totales; los reintentos son uno menos que eso
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, retry.maxAttempts() - 1));
        backOff.setInitialInterval(retry.initialIntervalMs());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxIntervalMs());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRecoverableMessageException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("[KAFKA REINTENTO] topic={} partition={} offset={} intento={}/{} causa={}",
                        record.topic(), record.partition(), record.offset(), attempt, retry.maxAttempts(),
                        ex.getMessage()));
        return handler;
    }

    /** Productor SOLO para la DLT: sabe enviar bytes crudos (JSON ilegible) y DTO. */
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> cfg = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);

        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper);
        json.setAddTypeInfo(false);
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());   // primero: mensajes que no se pudieron deserializar
        delegates.put(Object.class, json);                        // el resto: DTO -> JSON
        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(delegates, true);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg, new StringSerializer(), valueSerializer));
    }
}
