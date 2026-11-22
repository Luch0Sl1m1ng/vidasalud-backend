package com.vidasalud.kafkaadmin.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Cliente de administración de Kafka (AdminClient). Reutiliza la configuración de conexión que
 * Spring ya armó en KafkaAdmin (bootstrap servers, timeouts) a partir de spring.kafka.*.
 * El cliente no se conecta hasta la primera petición: la app arranca aunque Kafka esté apagado
 * y solo responde un 503 claro cuando llega una petición.
 */
@Configuration
@EnableConfigurationProperties(KafkaAdminProperties.class)
public class KafkaAdminClientConfig {

    @Bean(destroyMethod = "close")
    public Admin kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return Admin.create(kafkaAdmin.getConfigurationProperties());
    }
}
