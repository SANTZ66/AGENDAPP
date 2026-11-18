package com.fet.citas.relacion.persistencia;

import com.fet.citas.relacion.dominio.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    List<Notificacion> findByEstablecimientoIdOrderByCreadaEnDesc(UUID establecimientoId);

    /** Control de idempotencia ante la reentrega del mismo evento. */
    boolean existsByCitaIdAndTipo(UUID citaId, String tipo);
}
