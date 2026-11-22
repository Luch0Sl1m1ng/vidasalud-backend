package com.vidasalud.kafkaadmin.dto;

import java.util.List;

/** Estado del cluster: id, controller y brokers. */
public record ClusterInfoResponse(String clusterId, int controllerId, List<Broker> brokers) {

    public record Broker(int id, String host, int port) {
    }
}
