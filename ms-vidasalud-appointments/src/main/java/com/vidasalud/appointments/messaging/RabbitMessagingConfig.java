package com.vidasalud.appointments.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ del caso VidaSalud (6 colas = 3 flujos + 3 DLQ):
 *
 *   cmd.direct (direct) --email.send------> q.cmd.email     --(DLX)--> q.cmd.email.dlq
 *   cmd.direct (direct) --admission.ticket-> q.cmd.admission --(DLX)--> q.cmd.admission.dlq
 *   cmd.direct (direct) --record.gen-------> q.cmd.record    --(DLX)--> q.cmd.record.dlq
 *   cmd.topic  (topic)  --email.* / admission.# / record.*  hacia las mismas 3 colas
 *   cmd.dead.dlx (direct) reparte los mensajes rechazados a cada DLQ
 *
 * Spring (RabbitAdmin) declara estos beans en el broker al abrir la primera
 * conexión. Los NOMBRES vienen de application.yml (MessagingProperties).
 * El servicio de negocio NO conoce nada de esto: solo usa AppointmentEventPublisher.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMessagingConfig {

    private final MessagingProperties props;

    public RabbitMessagingConfig(MessagingProperties props) {
        this.props = props;
    }

    // ---------------- Exchanges ----------------

    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(props.exchanges().direct(), true, false);
    }

    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange(props.exchanges().topic(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(props.exchanges().dlx(), true, false);
    }

    // ---------------- Colas principales (con DLX configurado) ----------------

    @Bean
    public Queue emailQueue() {
        return mainQueue(props.queues().email(), props.dlqKeys().email());
    }

    @Bean
    public Queue admissionQueue() {
        return mainQueue(props.queues().admission(), props.dlqKeys().admission());
    }

    @Bean
    public Queue recordQueue() {
        return mainQueue(props.queues().record(), props.dlqKeys().record());
    }

    // ---------------- Dead Letter Queues ----------------

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(props.queues().emailDlq()).build();
    }

    @Bean
    public Queue admissionDlq() {
        return QueueBuilder.durable(props.queues().admissionDlq()).build();
    }

    @Bean
    public Queue recordDlq() {
        return QueueBuilder.durable(props.queues().recordDlq()).build();
    }

    // ---------------- Bindings: exchange direct ----------------

    @Bean
    public Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdDirectExchange()).with(props.routingKeys().email());
    }

    @Bean
    public Binding admissionDirectBinding() {
        return BindingBuilder.bind(admissionQueue()).to(cmdDirectExchange()).with(props.routingKeys().admission());
    }

    @Bean
    public Binding recordDirectBinding() {
        return BindingBuilder.bind(recordQueue()).to(cmdDirectExchange()).with(props.routingKeys().record());
    }

    // ---------------- Bindings: exchange topic ----------------

    @Bean
    public Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdTopicExchange()).with(props.topicPatterns().email());
    }

    @Bean
    public Binding admissionTopicBinding() {
        return BindingBuilder.bind(admissionQueue()).to(cmdTopicExchange()).with(props.topicPatterns().admission());
    }

    @Bean
    public Binding recordTopicBinding() {
        return BindingBuilder.bind(recordQueue()).to(cmdTopicExchange()).with(props.topicPatterns().record());
    }

    // ---------------- Bindings: DLX -> DLQ ----------------

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with(props.dlqKeys().email());
    }

    @Bean
    public Binding admissionDlqBinding() {
        return BindingBuilder.bind(admissionDlq()).to(deadLetterExchange()).with(props.dlqKeys().admission());
    }

    @Bean
    public Binding recordDlqBinding() {
        return BindingBuilder.bind(recordDlq()).to(deadLetterExchange()).with(props.dlqKeys().record());
    }

    // ---------------- Serialización JSON ----------------

    /** Spring Boot usa este bean automáticamente en RabbitTemplate. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    private Queue mainQueue(String name, String dlqRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", props.exchanges().dlx())
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }
}
