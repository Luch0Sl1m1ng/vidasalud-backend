package com.vidasalud.kafkaadmin.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return error(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "El cuerpo no es un JSON válido");
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

    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleKafkaDown(KafkaUnavailableException ex) {
        log.error("No hay conexión con Kafka: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_NO_DISPONIBLE",
                "No se pudo conectar con los brokers de Kafka");
    }

    @ExceptionHandler(KafkaOperationException.class)
    public ResponseEntity<Map<String, String>> handleKafkaError(KafkaOperationException ex) {
        log.error("Kafka rechazó la operación: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "KAFKA_ERROR", ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", codigo, "mensaje", mensaje));
    }
}
