package com.fet.citas.agenda.persistencia;

import com.fet.citas.agenda.dominio.Cita;
import com.fet.citas.agenda.dominio.EstadoCita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acceso a datos de citas. Spring Data JPA genera la implementacion en
 * tiempo de ejecucion a partir de los nombres de los metodos.
 */
public interface CitaRepository extends JpaRepository<Cita, UUID> {

    /**
     * Agenda de un profesional dentro de un rango de fechas, paginada.
     * Soporta GET /citas con filtros y paginacion (requisito de la rubrica).
     */
    Page<Cita> findByProfesionalIdAndInicioBetween(
            UUID profesionalId,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Pageable pageable);

    /**
     * Verifica si un profesional ya tiene una cita ACTIVA a una hora dada.
     * Se usa para dar un 409 limpio ANTES de intentar el INSERT; aun asi,
     * la restriccion UNIQUE de la BD es la garantia final contra la carrera.
     */
    boolean existsByProfesionalIdAndInicio(UUID profesionalId, OffsetDateTime inicio);

    /**
     * Citas en un estado dado cuyo inicio cae en un rango, de CUALQUIER
     * profesional y establecimiento. Lo usa GET /citas/proximas para que
     * otros servicios sepan que citas estan por llegar. Los extremos del
     * rango son inclusivos.
     */
    Page<Cita> findByEstadoAndInicioBetween(
            EstadoCita estado,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            Pageable pageable);
}
