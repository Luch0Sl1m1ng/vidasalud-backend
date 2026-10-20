package com.vidasalud.rabbitadmin.dto;

/** Estado de una cola: nombre, mensajes pendientes y consumidores conectados. */
public record QueueInfoResponse(String name, int messageCount, int consumerCount) {
}
