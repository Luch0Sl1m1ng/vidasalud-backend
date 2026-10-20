package com.vidasalud.notify.consumer;

import com.rabbitmq.client.Channel;
import com.vidasalud.notify.service.AdmissionTicketService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor de q.cmd.admission (ticket de admisión / llamado a box). */
@Component
public class AdmissionConsumer {

    private final ReliableMessageProcessor processor;
    private final AdmissionTicketService ticketService;

    public AdmissionConsumer(ReliableMessageProcessor processor, AdmissionTicketService ticketService) {
        this.processor = processor;
        this.ticketService = ticketService;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.admission}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.procesar("admission", message, channel, ticketService::emitir);
    }
}
