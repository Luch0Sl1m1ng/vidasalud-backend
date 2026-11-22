package com.vidasalud.kafkaadmin.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detalle de un topic: particiones (líder, réplicas, ISR) y configuración efectiva. */
public record TopicInfoResponse(
        String name,
        boolean internal,
        int partitions,
        int replicationFactor,
        List<PartitionInfo> partitionDetails,
        Map<String, String> configs,
        Set<String> overriddenConfigs) {

    public record PartitionInfo(int partition, int leader, List<Integer> replicas, List<Integer> isr) {
    }
}
