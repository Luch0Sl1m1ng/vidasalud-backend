package com.vidasalud.appointments.controller;

import com.vidasalud.appointments.dto.AppointmentRequest;
import com.vidasalud.appointments.dto.StatusUpdateRequest;
import com.vidasalud.appointments.model.Appointment;
import com.vidasalud.appointments.model.EstadoAtencion;
import com.vidasalud.appointments.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    // POST /api/appointments  -> crear atención (Paciente o Recepcionista)
    @PostMapping
    public ResponseEntity<Appointment> crear(@Valid @RequestBody AppointmentRequest request) {
        Appointment creada = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    // GET /api/appointments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Appointment> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    // GET /api/appointments?status=CONFIRMADA
    @GetMapping
    public ResponseEntity<List<Appointment>> listar(
            @RequestParam(required = false) EstadoAtencion status) {
        return ResponseEntity.ok(service.listar(status));
    }

    // PUT /api/appointments/{id}/status  -> cambiar estado (Recepcionista/Admin)
    @PutMapping("/{id}/status")
    public ResponseEntity<Appointment> cambiarEstado(@PathVariable Long id,
                                                       @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(service.cambiarEstado(id, request.getStatus()));
    }
}
