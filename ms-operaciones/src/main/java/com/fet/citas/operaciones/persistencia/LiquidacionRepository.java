package com.fet.citas.operaciones.persistencia;

import com.fet.citas.operaciones.dominio.Liquidacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, UUID> {

    /** Control de idempotencia: evita liquidar dos veces la misma cita. */
    boolean existsByCitaId(UUID citaId);

    List<Liquidacion> findByEstablecimientoIdAndFechaServicioBetween(
            UUID establecimientoId, OffsetDateTime desde, OffsetDateTime hasta);
}
