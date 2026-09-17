package com.vidasalud.catalog.repository;

import com.vidasalud.catalog.model.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrestacionRepository extends JpaRepository<Prestacion, Long> {
}
