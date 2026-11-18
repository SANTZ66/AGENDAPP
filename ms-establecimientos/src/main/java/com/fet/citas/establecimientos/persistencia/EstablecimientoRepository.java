package com.fet.citas.establecimientos.persistencia;

import com.fet.citas.establecimientos.dominio.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstablecimientoRepository extends JpaRepository<Establecimiento, UUID> {
}
