package com.vidasalud.kafkaadmin.dto;

import java.util.List;

/**
 * Detalle de un consumer group: estado, miembros (con las particiones que leen) y,
 * por partición, el offset confirmado, el último offset del log y el LAG (mensajes pendientes).
 */
public record ConsumerGroupResponse(
        String groupId,
        String state,
        String partitionAssignor,
        List<Member> members,
        List<OffsetInfo> offsets,
        long totalLag) {

    public record Member(String consumerId, String clientId, String host, List<String> partitions) {
    }

    public record OffsetInfo(String topic, int partition, long committedOffset, long logEndOffset, long lag) {
    }
}
