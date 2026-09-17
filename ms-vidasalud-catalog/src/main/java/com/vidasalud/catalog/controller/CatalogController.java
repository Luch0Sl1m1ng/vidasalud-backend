package com.vidasalud.catalog.controller;

import com.vidasalud.catalog.dto.PrestacionRequest;
import com.vidasalud.catalog.model.Prestacion;
import com.vidasalud.catalog.service.PrestacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final PrestacionService service;

    public CatalogController(PrestacionService service) {
        this.service = service;
    }

    // GET /api/catalog/services
    @GetMapping("/services")
    public ResponseEntity<List<Prestacion>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    // POST /api/catalog/services  (solo Admin, ver SecurityConfig)
    @PostMapping("/services")
    public ResponseEntity<Prestacion> crear(@Valid @RequestBody PrestacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    // PUT /api/catalog/services/{id}  (precio/cupo, solo Admin)
    @PutMapping("/services/{id}")
    public ResponseEntity<Prestacion> actualizar(@PathVariable Long id,
                                                   @Valid @RequestBody PrestacionRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }
}
