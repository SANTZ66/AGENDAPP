package com.fet.citas.simulador;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Un endpoint para ver de un vistazo que esta haciendo el simulador.
 * Util para la evidencia: http://localhost:8084/estado
 */
@RestController
public class EstadoController {

    private final Escenario escenario;
    private final ObjectProvider<GeneradorDeCitas> generador;

    public EstadoController(Escenario escenario, ObjectProvider<GeneradorDeCitas> generador) {
        this.escenario = escenario;
        this.generador = generador;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        Map<String, Object> salida = new HashMap<>();
        GeneradorDeCitas gen = generador.getIfAvailable();
        salida.put("activo", gen != null);
        salida.put("escenarioListo", escenario.estaListo());
        salida.put("establecimientoId", escenario.establecimientoId());
        salida.put("profesionalId", escenario.profesionalId());
        salida.put("servicioId", escenario.servicioId());
        salida.put("citasGeneradas", gen == null ? 0 : gen.generadas());
        return salida;
    }
}
