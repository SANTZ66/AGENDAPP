package com.fet.citas.gateway.seguridad;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Traduce las excepciones de seguridad a errores GraphQL con su clasificacion
 * (extensions.classification = UNAUTHORIZED o FORBIDDEN). El frontend decide por ese
 * codigo y no por el texto.
 *
 * Solo atiende las excepciones propias de este paquete: devuelve null para
 * todo lo demas, de modo que el resto de errores se comportan como siempre.
 */
@Component
public class ManejadorErroresGraphql extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable ex,
                                                @NonNull DataFetchingEnvironment env) {
        if (ex instanceof NoAutenticadoException) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.UNAUTHORIZED)
                    .message(ex.getMessage())
                    .build();
        }
        if (ex instanceof SinPermisoException) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.FORBIDDEN)
                    .message(ex.getMessage())
                    .build();
        }
        return null;
    }
}
