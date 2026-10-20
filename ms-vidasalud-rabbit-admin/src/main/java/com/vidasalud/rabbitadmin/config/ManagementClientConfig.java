package com.vidasalud.rabbitadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Cliente HTTP (con Basic Auth) hacia la API del Management UI de RabbitMQ, usado solo para listar. */
@Configuration
public class ManagementClientConfig {

    @Bean
    public RestClient rabbitManagementRestClient(
            @Value("${vidasalud.rabbit-admin.management-url}") String baseUrl,
            @Value("${spring.rabbitmq.username}") String user,
            @Value("${spring.rabbitmq.password}") String password) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(user, password))
                .build();
    }
}
