package com.vidasalud.notify.consumer;

import com.rabbitmq.client.Channel;
import com.vidasalud.notify.service.RecordDocumentService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor de q.cmd.record (generación de comprobante / resumen en PDF). */
@Component
public class RecordConsumer {

    private final ReliableMessageProcessor processor;
    private final RecordDocumentService documentService;

    public RecordConsumer(ReliableMessageProcessor processor, RecordDocumentService documentService) {
        this.processor = processor;
        this.documentService = documentService;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.record}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.procesar("record", message, channel, documentService::generar);
    }
}
