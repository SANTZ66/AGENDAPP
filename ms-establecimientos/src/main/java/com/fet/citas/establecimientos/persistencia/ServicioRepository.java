package com.fet.citas.establecimientos.persistencia;

import com.fet.citas.establecimientos.dominio.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicioRepository extends JpaRepository<Servicio, UUID> {
    List<Servicio> findByEstablecimientoId(UUID establecimientoId);
}
