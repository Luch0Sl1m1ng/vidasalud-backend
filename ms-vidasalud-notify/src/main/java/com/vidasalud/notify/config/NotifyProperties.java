package com.vidasalud.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros del consumidor (reintentos y simulación de fallos), prefijo "vidasalud.notify". */
@ConfigurationProperties(prefix = "vidasalud.notify")
public record NotifyProperties(boolean demoFailures, Retry retry) {

    public record Retry(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {}
}
