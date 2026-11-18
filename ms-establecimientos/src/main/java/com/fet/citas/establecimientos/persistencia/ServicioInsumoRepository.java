package com.fet.citas.establecimientos.persistencia;

import com.fet.citas.establecimientos.dominio.ServicioInsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicioInsumoRepository extends JpaRepository<ServicioInsumo, UUID> {
    List<ServicioInsumo> findByServicioId(UUID servicioId);
}
