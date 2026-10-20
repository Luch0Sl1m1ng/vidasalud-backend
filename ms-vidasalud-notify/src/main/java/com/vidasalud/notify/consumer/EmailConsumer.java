package com.vidasalud.notify.consumer;

import com.rabbitmq.client.Channel;
import com.vidasalud.notify.service.EmailNotificationService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor de q.cmd.email (el nombre de la cola viene de application.yml). */
@Component
public class EmailConsumer {

    private final ReliableMessageProcessor processor;
    private final EmailNotificationService emailService;

    public EmailConsumer(ReliableMessageProcessor processor, EmailNotificationService emailService) {
        this.processor = processor;
        this.emailService = emailService;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.email}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.procesar("email", message, channel, emailService::enviar);
    }
}
