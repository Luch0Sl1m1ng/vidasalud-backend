package com.vidasalud.appointments.service;

import com.vidasalud.appointments.dto.AppointmentRequest;
import com.vidasalud.appointments.exception.IllegalStateTransitionException;
import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import com.vidasalud.appointments.repository.AppointmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppointmentService {

    private final AppointmentRepository repository;

    // Transiciones válidas: desde -> hacia permitidas.
    // Esto es lo que exige el caso: "No se puede pasar a EN_ATENCION sin CONFIRMAR"
    private static final Map<EstadoAtencion, Set<EstadoAtencion>> TRANSICIONES_VALIDAS = Map.of(
            EstadoAtencion.SOLICITADA, Set.of(EstadoAtencion.CONFIRMADA, EstadoAtencion.CANCELADA),
            EstadoAtencion.CONFIRMADA, Set.of(EstadoAtencion.EN_ESPERA, EstadoAtencion.CANCELADA),
            EstadoAtencion.EN_ESPERA, Set.of(EstadoAtencion.EN_ATENCION, EstadoAtencion.CANCELADA),
            EstadoAtencion.EN_ATENCION, Set.of(EstadoAtencion.CERRADA),
            EstadoAtencion.CERRADA, Set.of(),
            EstadoAtencion.CANCELADA, Set.of()
    );

    public AppointmentService(AppointmentRepository repository) {
        this.repository = repository;
    }

    public Appointment crear(AppointmentRequest request) {
        Appointment appointment = new Appointment();
        appointment.setPacienteId(request.getPacienteId());
        appointment.setServicioId(request.getServicioId());
        appointment.setBoxId(request.getBoxId());
        appointment.setEstado(EstadoAtencion.SOLICITADA);
        return repository.save(appointment);
    }

    public Appointment obtenerPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Atención " + id + " no existe"));
    }

    public List<Appointment> listar(EstadoAtencion estado) {
        return estado != null ? repository.findByEstado(estado) : repository.findAll();
    }

    public Appointment cambiarEstado(Long id, EstadoAtencion nuevoEstado) {
        Appointment appointment = obtenerPorId(id);
        EstadoAtencion actual = appointment.getEstado();

        Set<EstadoAtencion> permitidos = TRANSICIONES_VALIDAS.getOrDefault(actual, Set.of());
        if (!permitidos.contains(nuevoEstado)) {
            throw new IllegalStateTransitionException(
                    "No se puede pasar de " + actual + " a " + nuevoEstado +
                    " (regla: EN_ATENCION requiere haber pasado por CONFIRMADA)");
        }

        appointment.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoAtencion.EN_ATENCION) {
            appointment.setFechaAtencion(LocalDateTime.now());
        }
        return repository.save(appointment);
    }
}
