package com.fet.citas.establecimientos.persistencia;

import com.fet.citas.establecimientos.dominio.Profesional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfesionalRepository extends JpaRepository<Profesional, UUID> {
    List<Profesional> findByEstablecimientoId(UUID establecimientoId);
}
