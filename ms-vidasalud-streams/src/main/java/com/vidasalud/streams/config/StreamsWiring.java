package com.vidasalud.streams.config;

import com.vidasalud.streams.topology.CitasTopology;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafkaStreams   // crea el StreamsBuilder y arranca Kafka Streams con spring.kafka.streams.*
@EnableConfigurationProperties(StreamsProperties.class)
public class StreamsWiring {

    /** Registra la topologia del pipeline en el StreamsBuilder que gestiona Spring. */
    @Autowired
    void registrarTopologia(StreamsBuilder builder, StreamsProperties props) {
        CitasTopology.build(builder, props);
    }

    // Los topics se crean al arrancar (la creacion automatica del broker esta desactivada).
    @Bean
    public NewTopic alertasTopic(StreamsProperties p) {
        return TopicBuilder.name(p.alertsTopic()).partitions(p.partitions()).replicas(p.replicas()).build();
    }

    @Bean
    public NewTopic citasPorPacienteTopic(StreamsProperties p) {
        return TopicBuilder.name(p.porPacienteTopic()).partitions(p.partitions()).replicas(p.replicas()).build();
    }

    /** Entrada: idempotente con la que crea appointments (mismo nombre y particiones). */
    @Bean
    public NewTopic citasEntradaTopic(StreamsProperties p) {
        return TopicBuilder.name(p.inputTopic()).partitions(p.partitions()).replicas(p.replicas()).build();
    }
}
