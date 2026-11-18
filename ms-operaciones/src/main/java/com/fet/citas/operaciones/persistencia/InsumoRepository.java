package com.fet.citas.operaciones.persistencia;

import com.fet.citas.operaciones.dominio.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    List<Insumo> findByEstablecimientoId(UUID establecimientoId);

    /** Insumos que ya alcanzaron su umbral de reposicion (RF-20). */
    @Query("select i from Insumo i where i.establecimientoId = ?1 "
            + "and i.existencia <= i.umbralMinimo")
    List<Insumo> findEnUmbral(UUID establecimientoId);
}
