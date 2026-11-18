package com.fet.citas.simulador;

import com.fet.citas.simulador.clientes.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Crea el escenario sobre el que trabajara el simulador: un establecimiento
 * propio, una profesional, un insumo y un servicio con su receta.
 *
 * Usa un establecimiento aparte para no mezclarse con los datos que ustedes
 * creen a mano o con demo.sh.
 *
 * El insumo se crea con existencia 20 y umbral 15: tras unas pocas citas
 * cerradas se cruza el umbral y se dispara tambien inventario.insumo-bajo,
 * de modo que el simulador alimenta los CUATRO topics del sistema.
 *
 * El bucle de reintentos es necesario: docker compose arranca los
 * contenedores en orden, pero "arrancado" no es lo mismo que "listo para
 * recibir peticiones". Spring Boot tarda unos segundos en levantar Tomcat.
 */
@Component
public class SemillaDatos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SemillaDatos.class);
    private static final int INTENTOS = 30;
    private static final long ESPERA_MS = 5000L;

    private final ApiClient api;
    private final Escenario escenario;

    public SemillaDatos(ApiClient api, Escenario escenario) {
        this.api = api;
        this.escenario = escenario;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                crear();
                return;
            } catch (Exception e) {
                log.info("Los servicios aun no responden (intento {}/{}): {}",
                        intento, INTENTOS, e.getMessage());
                dormir();
            }
        }
        log.error("No se pudo preparar el escenario. El simulador quedara inactivo.");
    }

    private void crear() {
        UUID establecimientoId = idDe(api.establecimientos.post()
                .uri("/establecimientos")
                .body(Map.of("nombre", "Establecimiento de simulacion",
                             "telefono", "3000000000",
                             "ventanaConfirmacionHoras", 24))
                .retrieve().body(Map.class));

        UUID profesionalId = idDe(api.establecimientos.post()
                .uri("/establecimientos/{id}/profesionales", establecimientoId)
                .body(Map.of("nombre", "Profesional simulada"))
                .retrieve().body(Map.class));

        UUID servicioId = idDe(api.establecimientos.post()
                .uri("/servicios")
                .body(Map.of("establecimientoId", establecimientoId,
                             "nombre", "Servicio simulado",
                             "duracionMinutos", 60,
                             "precio", 45000,
                             "periodicidadDias", 28))
                .retrieve().body(Map.class));

        // El insumo vive en ms-operaciones; la receta que lo asocia al
        // servicio vive en ms-establecimientos. Son dos servicios distintos.
        UUID insumoId = idDe(api.operaciones.post()
                .uri("/inventario")
                .body(Map.of("establecimientoId", establecimientoId,
                             "nombre", "Insumo simulado",
                             "unidad", "unidad",
                             "existencia", 20,
                             "umbralMinimo", 15))
                .retrieve().body(Map.class));

        api.establecimientos.post()
                .uri("/servicios/{id}/insumos", servicioId)
                .body(Map.of("insumoId", insumoId, "cantidadEstimada", 1))
                .retrieve().toBodilessEntity();

        escenario.completar(establecimientoId, profesionalId, servicioId, insumoId);
        log.info("Escenario listo. Establecimiento {} profesional {} servicio {} insumo {}",
                establecimientoId, profesionalId, servicioId, insumoId);
    }

    @SuppressWarnings("rawtypes")
    private UUID idDe(Map cuerpo) {
        return UUID.fromString(String.valueOf(cuerpo.get("id")));
    }

    private void dormir() {
        try {
            Thread.sleep(ESPERA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
