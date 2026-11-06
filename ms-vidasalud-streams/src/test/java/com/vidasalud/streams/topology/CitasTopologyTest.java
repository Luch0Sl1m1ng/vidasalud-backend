package com.vidasalud.streams.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidasalud.streams.config.StreamsProperties;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CitasTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final StreamsProperties PROPS =
            new StreamsProperties("in", "alertas", "por-paciente", 3, 3, (short) 1);

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> entrada;
    private TestOutputTopic<String, String> alertas;
    private TestOutputTopic<String, Long> porPaciente;

    @BeforeEach
    void setUp() throws Exception {
        StreamsBuilder builder = new StreamsBuilder();
        CitasTopology.build(builder, PROPS);

        Properties cfg = new Properties();
        cfg.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-vidasalud-streams");
        cfg.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        cfg.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        cfg.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        cfg.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("streams-test").toString());
        cfg.put("statestore.cache.max.bytes", 0); // emite cada actualizacion de la agregacion

        driver = new TopologyTestDriver(builder.build(), cfg);
        entrada = driver.createInputTopic("in", Serdes.String().serializer(), Serdes.String().serializer());
        alertas = driver.createOutputTopic("alertas", Serdes.String().deserializer(), Serdes.String().deserializer());
        porPaciente = driver.createOutputTopic("por-paciente", Serdes.String().deserializer(), Serdes.Long().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    private static String evento(long cita, String paciente, String anterior, String nuevo) {
        return "{\"type\":\"APPOINTMENT_STATE_CHANGED\",\"eventId\":\"e-" + cita + "\",\"payload\":{"
                + "\"appointmentId\":" + cita + ",\"pacienteId\":\"" + paciente + "\","
                + "\"estadoAnterior\":\"" + anterior + "\",\"estadoNuevo\":\"" + nuevo + "\"}}";
    }

    @Test
    void tresCancelacionesDelMismoPacienteGeneranAlerta() throws Exception {
        entrada.pipeInput("cita-1", evento(1, "p1", "CONFIRMADA", "CANCELADA"));
        entrada.pipeInput("cita-2", evento(2, "p1", "CONFIRMADA", "CANCELADA"));
        assertTrue(alertas.isEmpty(), "con 2 cancelaciones todavia no hay alerta");

        entrada.pipeInput("cita-3", evento(3, "p1", "SOLICITADA", "CANCELADA"));

        List<KeyValue<String, String>> out = alertas.readKeyValuesToList();
        assertEquals(1, out.size());
        assertEquals("p1", out.get(0).key);
        JsonNode alerta = MAPPER.readTree(out.get(0).value);
        assertEquals("p1", alerta.get("pacienteId").asText());
        assertEquals(3, alerta.get("cancelaciones").asLong());
        assertEquals("MEDIA", alerta.get("severidad").asText());
    }

    @Test
    void cancelacionesDePacientesDistintosNoSeMezclan() {
        entrada.pipeInput("cita-1", evento(1, "p1", "CONFIRMADA", "CANCELADA"));
        entrada.pipeInput("cita-2", evento(2, "p2", "CONFIRMADA", "CANCELADA"));
        entrada.pipeInput("cita-3", evento(3, "p3", "CONFIRMADA", "CANCELADA"));
        assertTrue(alertas.isEmpty());
    }

    @Test
    void cuentaEventosPorPaciente() {
        entrada.pipeInput("cita-1", evento(1, "p1", "SOLICITADA", "CONFIRMADA"));
        entrada.pipeInput("cita-2", evento(2, "p1", "CONFIRMADA", "EN_ESPERA"));
        entrada.pipeInput("cita-3", evento(3, "p2", "SOLICITADA", "CONFIRMADA"));

        List<KeyValue<String, Long>> out = porPaciente.readKeyValuesToList();
        assertEquals(3, out.size());
        assertEquals(new KeyValue<>("p1", 1L), out.get(0));
        assertEquals(new KeyValue<>("p1", 2L), out.get(1));
        assertEquals(new KeyValue<>("p2", 1L), out.get(2));
    }

    @Test
    void ignoraEventosSinCambioDeEstadoYJsonInvalido() {
        entrada.pipeInput("cita-1", evento(1, "p1", "CONFIRMADA", "CONFIRMADA")); // sin cambio real
        entrada.pipeInput("cita-2", "esto no es json");
        entrada.pipeInput("cita-3", "{\"payload\":{\"estadoNuevo\":\"CANCELADA\"}}"); // sin paciente
        assertTrue(porPaciente.isEmpty());
        assertTrue(alertas.isEmpty());
    }

    @Test
    void categoriaYParseoDeEvento() {
        assertEquals("CANCELACION", CitasTopology.categoria("CANCELADA"));
        assertEquals("CONFIRMACION", CitasTopology.categoria("CONFIRMADA"));
        assertEquals("CIERRE", CitasTopology.categoria("CERRADA"));
        assertEquals("ATENCION", CitasTopology.categoria("EN_ESPERA"));
        assertNull(CitasTopology.parsear("no-json"));
        assertFalse(CitasTopology.esRelevante(null));
    }
}
