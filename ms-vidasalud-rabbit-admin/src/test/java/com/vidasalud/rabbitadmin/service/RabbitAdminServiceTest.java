package com.vidasalud.rabbitadmin.service;

import com.vidasalud.rabbitadmin.dto.BindingRequest;
import com.vidasalud.rabbitadmin.dto.ExchangeRequest;
import com.vidasalud.rabbitadmin.dto.QueueInfoResponse;
import com.vidasalud.rabbitadmin.dto.QueueRequest;
import com.vidasalud.rabbitadmin.exception.ConflictException;
import com.vidasalud.rabbitadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitManagementClient managementClient;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        managementClient = mock(RabbitManagementClient.class);
        service = new RabbitAdminService(amqpAdmin, managementClient);
    }

    private void colaExiste(String nombre, int mensajes, int consumidores) {
        when(amqpAdmin.getQueueInfo(nombre)).thenReturn(new QueueInformation(nombre, mensajes, consumidores));
    }

    // ---------- colas ----------

    @Test
    void crearColaDeclaraColaDurableConDlxYTtl() {
        colaExiste("q.demo", 0, 0);

        QueueInfoResponse respuesta = service.crearCola(
                new QueueRequest("q.demo", null, null, "cmd.dead.dlx", "demo.dlq", 30000, 500));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue declarada = captor.getValue();
        assertEquals("q.demo", declarada.getName());
        assertTrue(declarada.isDurable());          // por defecto durable
        assertEquals("cmd.dead.dlx", declarada.getArguments().get("x-dead-letter-exchange"));
        assertEquals("demo.dlq", declarada.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(30000, declarada.getArguments().get("x-message-ttl"));
        assertEquals("q.demo", respuesta.name());
    }

    @Test
    void crearColaQueYaExisteConOtrosArgumentosEsConflicto() {
        doThrow(new AmqpIOException(new IOException("PRECONDITION_FAILED - inequivalent arg")))
                .when(amqpAdmin).declareQueue(any(Queue.class));

        assertThrows(ConflictException.class,
                () -> service.crearCola(new QueueRequest("q.demo", null, null, null, null, null, null)));
    }

    @Test
    void eliminarColaInexistenteEs404YNoLlamaAlBroker() {
        when(amqpAdmin.getQueueInfo("q.nada")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.eliminarCola("q.nada", false, false));
        verify(amqpAdmin, never()).deleteQueue(anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void eliminarColaExistenteLlamaADeleteQueue() {
        colaExiste("q.demo", 3, 0);

        service.eliminarCola("q.demo", true, false);

        verify(amqpAdmin).deleteQueue("q.demo", true, false);
    }

    @Test
    void purgarDevuelveCantidadDescartada() {
        colaExiste("q.demo", 5, 0);
        when(amqpAdmin.purgeQueue("q.demo")).thenReturn(5);

        assertEquals(5, service.purgarCola("q.demo"));
    }

    @Test
    void nombresReservadosOInvalidosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> service.eliminarCola("amq.gen-abc", false, false));
        assertThrows(IllegalArgumentException.class, () -> service.eliminarExchange("amq.direct"));
        assertThrows(IllegalArgumentException.class, () -> service.eliminarExchange("con espacios"));
        verify(amqpAdmin, never()).deleteExchange(anyString());
    }

    // ---------- exchanges ----------

    @Test
    void crearExchangeTopicDeclaraUnTopicExchangeDurable() {
        service.crearExchange(new ExchangeRequest("ex.demo", "topic", null, null));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        assertInstanceOf(TopicExchange.class, captor.getValue());
        assertEquals("ex.demo", captor.getValue().getName());
        assertTrue(captor.getValue().isDurable());
    }

    @Test
    void eliminarExchangeLlamaADeleteExchange() {
        service.eliminarExchange("ex.demo");
        verify(amqpAdmin).deleteExchange("ex.demo");
    }

    // ---------- bindings ----------

    @Test
    void crearBindingHaciaColaInexistenteEs404() {
        when(amqpAdmin.getQueueInfo("q.nada")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.crearBinding(
                new BindingRequest("cmd.direct", "q.nada", Binding.DestinationType.QUEUE, "demo.key")));
        verify(amqpAdmin, never()).declareBinding(any(Binding.class));
    }

    @Test
    void crearYEliminarBindingUsanLosMismosDatos() {
        colaExiste("q.demo", 0, 0);
        BindingRequest request = new BindingRequest("cmd.topic", "q.demo", Binding.DestinationType.QUEUE, "demo.#");

        service.crearBinding(request);
        service.eliminarBinding(request);

        ArgumentCaptor<Binding> creado = ArgumentCaptor.forClass(Binding.class);
        ArgumentCaptor<Binding> eliminado = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(creado.capture());
        verify(amqpAdmin).removeBinding(eliminado.capture());
        assertEquals("cmd.topic", creado.getValue().getExchange());
        assertEquals("q.demo", creado.getValue().getDestination());
        assertEquals("demo.#", creado.getValue().getRoutingKey());
        assertEquals(creado.getValue().getExchange(), eliminado.getValue().getExchange());
        assertEquals(creado.getValue().getDestination(), eliminado.getValue().getDestination());
        assertEquals(creado.getValue().getRoutingKey(), eliminado.getValue().getRoutingKey());
    }

    // ---------- listados ----------

    @Test
    void listarColasDelegaEnElClienteDeGestion() {
        List<Map<String, Object>> esperado = List.of(Map.of("name", "q.demo"));
        when(managementClient.listarColas()).thenReturn(esperado);

        assertEquals(esperado, service.listarColas());
    }
}
