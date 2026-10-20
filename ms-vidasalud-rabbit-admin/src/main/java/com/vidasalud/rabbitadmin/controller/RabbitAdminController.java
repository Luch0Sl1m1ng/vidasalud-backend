package com.vidasalud.rabbitadmin.controller;

import com.vidasalud.rabbitadmin.dto.BindingRequest;
import com.vidasalud.rabbitadmin.dto.ExchangeRequest;
import com.vidasalud.rabbitadmin.dto.QueueInfoResponse;
import com.vidasalud.rabbitadmin.dto.QueueRequest;
import com.vidasalud.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API para administrar RabbitMQ (solo rol Admin). El controller NO tiene lógica:
 * valida el request con @Valid y delega todo en RabbitAdminService.
 */
@RestController
@RequestMapping("/api/mq")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    // ---------- Colas ----------

    @PostMapping("/queues")
    public ResponseEntity<QueueInfoResponse> crearCola(@Valid @RequestBody QueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCola(request));
    }

    @GetMapping("/queues")
    public List<Map<String, Object>> listarColas() {
        return service.listarColas();
    }

    @GetMapping("/queues/{name}")
    public QueueInfoResponse obtenerCola(@PathVariable String name) {
        return service.obtenerCola(name);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String name,
                                             @RequestParam(defaultValue = "false") boolean ifUnused,
                                             @RequestParam(defaultValue = "false") boolean ifEmpty) {
        service.eliminarCola(name, ifUnused, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/queues/{name}/purge")
    public Map<String, Object> purgarCola(@PathVariable String name) {
        return Map.of("queue", name, "purged", service.purgarCola(name));
    }

    // ---------- Exchanges ----------

    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        service.crearExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("name", request.name(), "type", request.type()));
    }

    @GetMapping("/exchanges")
    public List<Map<String, Object>> listarExchanges() {
        return service.listarExchanges();
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String name) {
        service.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }

    // ---------- Bindings ----------

    @PostMapping("/bindings")
    public ResponseEntity<BindingRequest> crearBinding(@Valid @RequestBody BindingRequest request) {
        service.crearBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/bindings")
    public List<Map<String, Object>> listarBindings() {
        return service.listarBindings();
    }

    /** DELETE con cuerpo JSON: identifica el binding por exchange + destination + destinationType + routingKey. */
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingRequest request) {
        service.eliminarBinding(request);
        return ResponseEntity.noContent().build();
    }
}
