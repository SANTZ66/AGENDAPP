package com.fet.citas.operaciones.persistencia;

import com.fet.citas.operaciones.dominio.MovimientoInsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimientoInsumoRepository extends JpaRepository<MovimientoInsumo, UUID> {
    boolean existsByCitaId(UUID citaId);
}
