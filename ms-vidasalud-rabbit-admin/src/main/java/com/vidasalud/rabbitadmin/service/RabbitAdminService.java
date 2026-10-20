package com.vidasalud.rabbitadmin.service;

import com.vidasalud.rabbitadmin.dto.BindingRequest;
import com.vidasalud.rabbitadmin.dto.ExchangeRequest;
import com.vidasalud.rabbitadmin.dto.NamePolicy;
import com.vidasalud.rabbitadmin.dto.QueueInfoResponse;
import com.vidasalud.rabbitadmin.dto.QueueRequest;
import com.vidasalud.rabbitadmin.exception.ConflictException;
import com.vidasalud.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * TODA la lógica para crear y eliminar colas, exchanges y bindings vive aquí.
 * El controller solo recibe el request validado y delega; no conoce AmqpAdmin.
 * Usa AmqpAdmin (RabbitAdmin de Spring AMQP) contra el broker por AMQP.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    /** Los recursos "amq.*" son del propio RabbitMQ y no se tocan. */
    private static final String PREFIJO_RESERVADO = "amq.";

    private final AmqpAdmin amqpAdmin;
    private final RabbitManagementClient managementClient;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitManagementClient managementClient) {
        this.amqpAdmin = amqpAdmin;
        this.managementClient = managementClient;
    }

    // ===================== COLAS =====================

    public QueueInfoResponse crearCola(QueueRequest request) {
        validarNombre(request.name());

        QueueBuilder builder = esVerdadero(request.durable(), true)
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());
        if (esVerdadero(request.autoDelete(), false)) {
            builder.autoDelete();
        }
        if (hayTexto(request.deadLetterExchange())) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (hayTexto(request.deadLetterRoutingKey())) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }

        Queue queue = builder.build();
        try {
            amqpAdmin.declareQueue(queue);
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "crear la cola '" + request.name() + "'");
        }
        log.info("[ADMIN] cola creada: {}", request.name());
        return infoDe(request.name());
    }

    public QueueInfoResponse obtenerCola(String nombre) {
        validarNombre(nombre);
        return infoDe(nombre);
    }

    public void eliminarCola(String nombre, boolean soloSiSinConsumidores, boolean soloSiVacia) {
        validarNombre(nombre);
        infoDe(nombre);   // lanza 404 si no existe
        try {
            amqpAdmin.deleteQueue(nombre, soloSiSinConsumidores, soloSiVacia);
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "eliminar la cola '" + nombre + "'");
        }
        log.info("[ADMIN] cola eliminada: {}", nombre);
    }

    /** Vacía la cola y devuelve cuántos mensajes se descartaron. */
    public int purgarCola(String nombre) {
        validarNombre(nombre);
        infoDe(nombre);   // lanza 404 si no existe
        try {
            int purgados = amqpAdmin.purgeQueue(nombre);
            log.info("[ADMIN] cola purgada: {} ({} mensajes)", nombre, purgados);
            return purgados;
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "purgar la cola '" + nombre + "'");
        }
    }

    public List<Map<String, Object>> listarColas() {
        return managementClient.listarColas();
    }

    // ===================== EXCHANGES =====================

    public void crearExchange(ExchangeRequest request) {
        validarNombre(request.name());

        boolean durable = esVerdadero(request.durable(), true);
        boolean autoDelete = esVerdadero(request.autoDelete(), false);
        Exchange exchange = switch (request.type()) {
            case "direct" -> new DirectExchange(request.name(), durable, autoDelete);
            case "topic" -> new TopicExchange(request.name(), durable, autoDelete);
            case "fanout" -> new FanoutExchange(request.name(), durable, autoDelete);
            case "headers" -> new HeadersExchange(request.name(), durable, autoDelete);
            default -> throw new IllegalArgumentException("Tipo de exchange no soportado: " + request.type());
        };

        try {
            amqpAdmin.declareExchange(exchange);
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "crear el exchange '" + request.name() + "'");
        }
        log.info("[ADMIN] exchange creado: {} ({})", request.name(), request.type());
    }

    /** RabbitMQ trata el borrado de un exchange como idempotente: si no existía, tampoco falla. */
    public void eliminarExchange(String nombre) {
        validarNombre(nombre);
        try {
            amqpAdmin.deleteExchange(nombre);
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "eliminar el exchange '" + nombre + "'");
        }
        log.info("[ADMIN] exchange eliminado: {}", nombre);
    }

    public List<Map<String, Object>> listarExchanges() {
        return managementClient.listarExchanges();
    }

    // ===================== BINDINGS =====================

    public void crearBinding(BindingRequest request) {
        validarNombre(request.exchange());
        validarNombre(request.destination());
        if (request.destinationType() == Binding.DestinationType.QUEUE) {
            infoDe(request.destination());   // 404 si la cola destino no existe
        }
        try {
            amqpAdmin.declareBinding(aBinding(request));
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "crear el binding " + request.exchange() + " -> " + request.destination());
        }
        log.info("[ADMIN] binding creado: {} -[{}]-> {} ({})", request.exchange(),
                routingKeyDe(request), request.destination(), request.destinationType());
    }

    public void eliminarBinding(BindingRequest request) {
        validarNombre(request.exchange());
        validarNombre(request.destination());
        try {
            amqpAdmin.removeBinding(aBinding(request));
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "eliminar el binding " + request.exchange() + " -> " + request.destination());
        }
        log.info("[ADMIN] binding eliminado: {} -[{}]-> {}", request.exchange(),
                routingKeyDe(request), request.destination());
    }

    public List<Map<String, Object>> listarBindings() {
        return managementClient.listarBindings();
    }

    // ===================== helpers =====================

    private Binding aBinding(BindingRequest request) {
        return new Binding(request.destination(), request.destinationType(),
                request.exchange(), routingKeyDe(request), null);
    }

    private String routingKeyDe(BindingRequest request) {
        return request.routingKey() == null ? "" : request.routingKey();
    }

    /** Devuelve el estado de la cola o lanza 404 si no existe. */
    private QueueInfoResponse infoDe(String nombre) {
        QueueInformation info;
        try {
            info = amqpAdmin.getQueueInfo(nombre);
        } catch (AmqpConnectException ex) {
            throw ex;
        } catch (AmqpException ex) {
            throw traducir(ex, "consultar la cola '" + nombre + "'");
        }
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + nombre + "' no existe");
        }
        return new QueueInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    private void validarNombre(String nombre) {
        if (!NamePolicy.esNombreValido(nombre)) {
            throw new IllegalArgumentException("Nombre inválido: " + NamePolicy.NAME_MESSAGE);
        }
        if (nombre.startsWith(PREFIJO_RESERVADO)) {
            throw new IllegalArgumentException(
                    "Los recursos que empiezan con '" + PREFIJO_RESERVADO + "' son del sistema y no se pueden administrar");
        }
    }

    /** Convierte el error del broker en 404 (NOT_FOUND) o 409 (PRECONDITION_FAILED, en uso, etc.). */
    private RuntimeException traducir(AmqpException ex, String accion) {
        String causa = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        log.warn("[ADMIN] el broker rechazó al {}: {}", accion, causa);
        if (causa != null && causa.contains("NOT_FOUND")) {
            return new ResourceNotFoundException("No existe el recurso al " + accion);
        }
        return new ConflictException("RabbitMQ rechazó la operación al " + accion
                + " (ya existe con otros argumentos, o está en uso)");
    }

    private boolean esVerdadero(Boolean valor, boolean porDefecto) {
        return valor == null ? porDefecto : valor;
    }

    private boolean hayTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
