package com.vidasalud.rabbitadmin.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding.DestinationType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private <T> boolean tieneError(Set<ConstraintViolation<T>> errores, String campo) {
        return errores.stream().anyMatch(e -> e.getPropertyPath().toString().equals(campo));
    }

    @Test
    void colaValidaNoTieneErrores() {
        QueueRequest ok = new QueueRequest("q.demo.test", true, false, "cmd.dead.dlx", "demo.dlq", 60000, 1000);
        assertTrue(validator.validate(ok).isEmpty());
    }

    @Test
    void colaSoloConNombreEsValida() {
        assertTrue(validator.validate(new QueueRequest("q.demo", null, null, null, null, null, null)).isEmpty());
    }

    @Test
    void nombreVacioOConCaracteresPeligrososSeRechaza() {
        assertTrue(tieneError(validator.validate(new QueueRequest("", null, null, null, null, null, null)), "name"));
        assertTrue(tieneError(validator.validate(new QueueRequest("cola con espacios", null, null, null, null, null, null)), "name"));
        assertTrue(tieneError(validator.validate(new QueueRequest("../etc/passwd", null, null, null, null, null, null)), "name"));
    }

    @Test
    void ttlYMaxLengthDebenSerPositivos() {
        Set<ConstraintViolation<QueueRequest>> errores =
                validator.validate(new QueueRequest("q.demo", null, null, null, null, 0, -5));
        assertTrue(tieneError(errores, "messageTtlMs"));
        assertTrue(tieneError(errores, "maxLength"));
    }

    @Test
    void exchangeSoloAceptaTiposConocidos() {
        assertTrue(validator.validate(new ExchangeRequest("ex.demo", "topic", true, false)).isEmpty());
        assertFalse(validator.validate(new ExchangeRequest("ex.demo", "inventado", true, false)).isEmpty());
        assertTrue(tieneError(validator.validate(new ExchangeRequest("ex.demo", "", null, null)), "type"));
    }

    @Test
    void bindingExigeExchangeDestinoYTipo() {
        Set<ConstraintViolation<BindingRequest>> errores =
                validator.validate(new BindingRequest("", "", null, "ok"));
        assertTrue(tieneError(errores, "exchange"));
        assertTrue(tieneError(errores, "destination"));
        assertTrue(tieneError(errores, "destinationType"));
    }

    @Test
    void routingKeyAdmiteComodinesPeroNoEspacios() {
        assertTrue(validator.validate(new BindingRequest("cmd.topic", "q.demo", DestinationType.QUEUE, "admission.#")).isEmpty());
        assertTrue(validator.validate(new BindingRequest("cmd.topic", "q.demo", DestinationType.QUEUE, "email.*")).isEmpty());
        assertTrue(validator.validate(new BindingRequest("cmd.fanout", "q.demo", DestinationType.QUEUE, null)).isEmpty());
        assertTrue(tieneError(validator.validate(new BindingRequest("cmd.topic", "q.demo", DestinationType.QUEUE, "a b")), "routingKey"));
    }
}
