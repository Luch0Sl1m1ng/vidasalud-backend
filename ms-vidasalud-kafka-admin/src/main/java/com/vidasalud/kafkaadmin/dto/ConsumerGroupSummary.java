package com.vidasalud.kafkaadmin.dto;

/** Fila del listado de consumer groups. */
public record ConsumerGroupSummary(String groupId, String state) {
}
