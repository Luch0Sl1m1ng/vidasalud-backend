package com.vidasalud.rabbitadmin.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce cada error a un código HTTP y un JSON claro (sin stack traces hacia el cliente). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> detalles.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "VALIDACION");
        body.put("mensaje", "Hay campos inválidos en la solicitud");
        body.put("detalles", detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "El cuerpo no es un JSON válido o tiene un valor no permitido (revisa tipos y enums)");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICTO", ex.getMessage());
    }

    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<Map<String, String>> handleBrokerDown(AmqpConnectException ex) {
        log.error("No hay conexión con RabbitMQ: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "RABBITMQ_NO_DISPONIBLE",
                "No se pudo conectar con RabbitMQ");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> handleManagementDown(ResourceAccessException ex) {
        log.error("No hay conexión con la API de gestión de RabbitMQ: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MANAGEMENT_NO_DISPONIBLE",
                "No se pudo conectar con la API de gestión de RabbitMQ (puerto 15672)");
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleManagementError(RestClientResponseException ex) {
        log.error("La API de gestión respondió {}", ex.getStatusCode());
        return error(HttpStatus.BAD_GATEWAY, "MANAGEMENT_ERROR",
                "La API de gestión de RabbitMQ respondió " + ex.getStatusCode().value());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", codigo, "mensaje", mensaje));
    }
}
