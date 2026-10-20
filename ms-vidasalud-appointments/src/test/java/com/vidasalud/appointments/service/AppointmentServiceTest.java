package com.vidasalud.appointments.service;

import com.vidasalud.appointments.dto.AppointmentRequest;
import com.vidasalud.appointments.exception.IllegalStateTransitionException;
import com.vidasalud.appointments.messaging.AppointmentEventPublisher;
import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import com.vidasalud.appointments.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {

    private AppointmentRepository repository;
    private AppointmentEventPublisher publisher;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppointmentRepository.class);
        publisher = mock(AppointmentEventPublisher.class);
        service = new AppointmentService(repository, publisher);
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cambioDeEstadoValidoPublicaEventoConEstadoAnterior() {
        Appointment cita = new Appointment();
        cita.setId(1L);
        cita.setEstado(EstadoAtencion.SOLICITADA);
        when(repository.findById(1L)).thenReturn(Optional.of(cita));

        Appointment resultado = service.cambiarEstado(1L, EstadoAtencion.CONFIRMADA);

        assertEquals(EstadoAtencion.CONFIRMADA, resultado.getEstado());
        verify(publisher).publicarCambio(any(Appointment.class), eq(EstadoAtencion.SOLICITADA));
    }

    @Test
    void transicionInvalidaNoPublicaNada() {
        Appointment cita = new Appointment();
        cita.setId(2L);
        cita.setEstado(EstadoAtencion.SOLICITADA);
        when(repository.findById(2L)).thenReturn(Optional.of(cita));

        assertThrows(IllegalStateTransitionException.class,
                () -> service.cambiarEstado(2L, EstadoAtencion.EN_ATENCION));

        verify(publisher, never()).publicarCambio(any(), any());
    }

    @Test
    void crearPublicaEventoDeSolicitud() {
        AppointmentRequest request = new AppointmentRequest();
        request.setPacienteId("paciente-1");
        request.setServicioId(3L);

        Appointment creada = service.crear(request);

        assertEquals(EstadoAtencion.SOLICITADA, creada.getEstado());
        verify(publisher).publicarCambio(any(Appointment.class), isNull());
    }
}
