package com.vidasalud.streams.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidasalud.streams.config.StreamsProperties;
import com.vidasalud.streams.model.AlertaCita;
import com.vidasalud.streams.model.CitaEvento;
import java.time.Instant;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Pipeline de la Guia 3.2.2 aplicado al caso VidaSalud.
 *
 * <pre>
 *  vidasalud.citas (JSON envelope)
 *     |  PARSEAR + FILTRAR   (descarta JSON invalido y eventos sin cambio real de estado)   [guia: monto &lt; 10]
 *     |  ENRIQUECER          (categoria: CANCELACION / CONFIRMACION / CIERRE / ATENCION)    [guia: moneda a USD]
 *     |  RE-CLAVAR           (key = pacienteId)
 *     +--> AGREGAR eventos por paciente  --> vidasalud.citas-por-paciente                  [guia: total por usuario]
 *     +--> AGREGAR cancelaciones por paciente --(>= umbral)--> vidasalud.alertas            [guia: SOSPECHOSA -> alertas]
 * </pre>
 */
public final class CitasTopology {

    private static final Logger log = LoggerFactory.getLogger(CitasTopology.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CitasTopology() {
    }

    public static void build(StreamsBuilder builder, StreamsProperties props) {

        // Serdes JSON (sin cabeceras de tipo: la clase destino se fija aqui)
        JsonSerde<CitaEvento> citaSerde = new JsonSerde<>(CitaEvento.class, MAPPER).ignoreTypeHeaders().noTypeInfo();
        JsonSerde<AlertaCita> alertaSerde = new JsonSerde<>(AlertaCita.class, MAPPER).ignoreTypeHeaders().noTypeInfo();

        // PASO 1: LEER (source). Llega el envelope JSON tal cual lo publico appointments.
        KStream<String, String> entrada = builder.stream(
                props.inputTopic(), Consumed.with(Serdes.String(), Serdes.String()));

        // PASO 2: PROCESAR
        // A. Parsear y filtrar (ruido: JSON ilegible, sin paciente, o sin cambio real de estado)
        KStream<String, CitaEvento> validos = entrada
                .mapValues(CitasTopology::parsear)
                .filter((key, ev) -> esRelevante(ev))
                // B. Enriquecer: ya viene con la categoria calculada en parsear()
                .peek((key, ev) -> log.info("[STREAMS] evento procesado cita={} paciente={} {}->{} categoria={}",
                        ev.appointmentId(), ev.pacienteId(), ev.estadoAnterior(), ev.estadoNuevo(), ev.categoria()));

        // C. Re-clavar por paciente (para agregar "por usuario"; garantiza orden por paciente)
        KStream<String, CitaEvento> porPaciente = validos
                .selectKey((key, ev) -> ev.pacienteId())
                .repartition(Repartitioned.with(Serdes.String(), citaSerde).withName("citas-por-paciente"));

        // D. Agregacion 1: total de eventos por paciente, en tiempo real
        porPaciente
                .groupByKey(Grouped.with(Serdes.String(), citaSerde))
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("eventos-por-paciente-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()))
                .toStream()
                .peek((paciente, total) -> log.info("[STREAMS] TOTAL eventos paciente={} = {}", paciente, total))
                .to(props.porPacienteTopic(), Produced.with(Serdes.String(), Serdes.Long()));

        // E. Agregacion 2 + deteccion: cancelaciones por paciente; si llega al umbral => alerta
        KStream<String, AlertaCita> alertas = porPaciente
                .filter((paciente, ev) -> "CANCELACION".equals(ev.categoria()))
                .groupByKey(Grouped.with(Serdes.String(), citaSerde))
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("cancelaciones-por-paciente-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()))
                .toStream()
                .filter((paciente, total) -> total != null && total >= props.umbralCancelaciones())
                .mapValues((paciente, total) -> new AlertaCita(
                        paciente,
                        total,
                        props.umbralCancelaciones(),
                        "Paciente con cancelaciones reiteradas (" + total + ")",
                        total >= props.umbralCancelaciones() * 2L ? "ALTA" : "MEDIA",
                        Instant.now().toString()));

        // PASO 3: ESCRIBIR (sink) + log para ver que sale
        alertas.peek((paciente, a) -> log.warn("[STREAMS] ALERTA paciente={} severidad={} motivo={}",
                paciente, a.severidad(), a.motivo()));
        alertas.to(props.alertsTopic(), Produced.with(Serdes.String(), alertaSerde));
    }

    /** Convierte el envelope JSON en CitaEvento; devuelve null si no se puede leer (se filtra despues). */
    static CitaEvento parsear(String json) {
        try {
            JsonNode raiz = MAPPER.readTree(json);
            JsonNode payload = raiz.path("payload");
            String paciente = texto(payload, "pacienteId");
            String anterior = texto(payload, "estadoAnterior");
            String nuevo = texto(payload, "estadoNuevo");
            return new CitaEvento(
                    texto(raiz, "eventId"),
                    payload.path("appointmentId").isNumber() ? payload.path("appointmentId").asLong() : null,
                    paciente,
                    anterior,
                    nuevo,
                    categoria(nuevo));
        } catch (Exception e) {
            log.warn("[STREAMS] mensaje ilegible, se descarta: {}", e.getMessage());
            return null;
        }
    }

    static boolean esRelevante(CitaEvento ev) {
        return ev != null
                && ev.pacienteId() != null && !ev.pacienteId().isBlank()
                && ev.estadoNuevo() != null && !ev.estadoNuevo().isBlank()
                && !ev.estadoNuevo().equals(ev.estadoAnterior());
    }

    static String categoria(String estadoNuevo) {
        if (estadoNuevo == null) {
            return "DESCONOCIDA";
        }
        return switch (estadoNuevo) {
            case "CANCELADA" -> "CANCELACION";
            case "CONFIRMADA" -> "CONFIRMACION";
            case "CERRADA" -> "CIERRE";
            default -> "ATENCION";
        };
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
