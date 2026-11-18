package com.fet.citas.gateway.seguridad;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Lee la cabecera Authorization: Bearer y deja el resultado en el contexto
 * de GraphQL:
 *
 *   sesion        la Sesion, si el token es valido
 *   sesionError   "vencida" o "invalida", si habia token y no se acepto
 *
 * NUNCA rechaza la peticion. Quien decide si una operacion exige sesion es
 * cada operacion (ver Exigir): una consulta publica con un token vencido, por
 * ejemplo una pestana del cliente abierta en el mismo navegador, tiene que
 * seguir funcionando.
 */
@Component
public class InterceptorSesion implements WebGraphQlInterceptor {

    public static final String SESION = "sesion";
    public static final String SESION_ERROR = "sesionError";
    public static final String VENCIDA = "vencida";
    public static final String INVALIDA = "invalida";

    private static final String PREFIJO = "bearer ";

    private final EmisorJwt emisor;

    public InterceptorSesion(EmisorJwt emisor) {
        this.emisor = emisor;
    }

    @Override
    @NonNull
    public Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
        String cabecera = request.getHeaders().getFirst("Authorization");
        if (cabecera == null || cabecera.isBlank()) {
            return chain.next(request);
        }

        Map<String, Object> contexto;
        if (cabecera.length() > PREFIJO.length()
                && cabecera.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            try {
                contexto = Map.of(SESION, emisor.validar(cabecera.substring(PREFIJO.length()).trim()));
            } catch (TokenInvalidoException e) {
                contexto = Map.of(SESION_ERROR, e.vencido() ? VENCIDA : INVALIDA);
            }
        } else {
            contexto = Map.of(SESION_ERROR, INVALIDA);
        }

        Map<String, Object> deLaPeticion = contexto;
        request.configureExecutionInput((input, builder) ->
                builder.graphQLContext(deLaPeticion).build());
        return chain.next(request);
    }
}
