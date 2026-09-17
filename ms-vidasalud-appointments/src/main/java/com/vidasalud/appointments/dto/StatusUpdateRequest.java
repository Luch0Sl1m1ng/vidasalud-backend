package com.vidasalud.appointments.dto;

import com.vidasalud.appointments.model.EstadoAtencion;
import jakarta.validation.constraints.NotNull;

public class StatusUpdateRequest {

    @NotNull
    private EstadoAtencion status;

    public EstadoAtencion getStatus() { return status; }
    public void setStatus(EstadoAtencion status) { this.status = status; }
}
