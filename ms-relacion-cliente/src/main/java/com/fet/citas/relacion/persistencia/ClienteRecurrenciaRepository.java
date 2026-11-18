package com.fet.citas.relacion.persistencia;

import com.fet.citas.relacion.dominio.ClienteRecurrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRecurrenciaRepository extends JpaRepository<ClienteRecurrencia, UUID> {

    Optional<ClienteRecurrencia> findByEstablecimientoIdAndClienteId(
            UUID establecimientoId, UUID clienteId);

    /** Clientes cuyo recontacto ya vencio y no han vuelto (RF-28). */
    @Query("select c from ClienteRecurrencia c where c.establecimientoId = ?1 "
            + "and c.proximoContacto is not null and c.proximoContacto < ?2")
    List<ClienteRecurrencia> findInactivos(UUID establecimientoId, OffsetDateTime corte);
}
