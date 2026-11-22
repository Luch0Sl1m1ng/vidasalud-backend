package com.vidasalud.kafkaadmin.dto;

/** Fila del listado de topics. */
public record TopicSummary(String name, boolean internal, int partitions, int replicationFactor) {
}
