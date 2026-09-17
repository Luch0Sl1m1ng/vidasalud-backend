package com.vidasalud.catalog.service;

import com.vidasalud.catalog.dto.PrestacionRequest;
import com.vidasalud.catalog.model.Prestacion;
import com.vidasalud.catalog.repository.PrestacionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PrestacionService {

    private final PrestacionRepository repository;

    public PrestacionService(PrestacionRepository repository) {
        this.repository = repository;
    }

    public List<Prestacion> listar() {
        return repository.findAll();
    }

    public Prestacion crear(PrestacionRequest request) {
        Prestacion p = new Prestacion();
        copiar(request, p);
        return repository.save(p);
    }

    public Prestacion actualizar(Long id, PrestacionRequest request) {
        Prestacion p = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prestación " + id + " no existe"));
        copiar(request, p);
        return repository.save(p);
    }

    // Se usa cuando se CONFIRMA una atención (llamado desde el flujo de negocio,
    // vía evento o llamada directa según cómo integren los servicios).
    public void disminuirCupo(Long prestacionId) {
        Prestacion p = repository.findById(prestacionId)
                .orElseThrow(() -> new EntityNotFoundException("Prestación " + prestacionId + " no existe"));
        if (p.getCupoDisponible() <= 0) {
            throw new IllegalStateException("Sin cupos disponibles para " + p.getNombre());
        }
        p.setCupoDisponible(p.getCupoDisponible() - 1);
        repository.save(p);
    }

    private void copiar(PrestacionRequest request, Prestacion p) {
        p.setNombre(request.getNombre());
        p.setPrecio(request.getPrecio());
        p.setBoxId(request.getBoxId());
        p.setCupoDisponible(request.getCupoDisponible());
    }
}
