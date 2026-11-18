# 3. Flujo de login

```mermaid
sequenceDiagram
  autonumber
  participant N as Navegador
  participant F as frontend (nginx)
  participant G as gateway-graphql
  participant E as ms-establecimientos
  N->>F: /negocio/login (correo, clave)
  F->>G: POST /graphql, mutation login(correo, clave)
  G->>E: POST /auth/verificar {correo, clave}
  alt credenciales validas
    E-->>G: usuarioId, nombre, establecimientoId, rol
    Note over G: EmisorJwt firma JWT HS256<br/>claims: sub, est, nom, rol, exp
    G-->>N: SesionNegocio (token, expiraEn, nombre, establecimientoId, rol)
  else 401 o 400
    E-->>G: error 4xx
    G-->>N: NoAutenticado "Correo o clave incorrectos"
  end
  N->>G: GraphQL con Authorization: Bearer token
  Note over G: InterceptorSesion valida el token<br/>y Exigir decide por operacion
  G-->>N: datos, o error de sesion vencida / invalida
```

![Login](img/03-login.png)

## Verificado contra

| Elemento | Dónde se comprobó |
|---|---|
| Mutación pública `login(correo, clave)` | `schema.graphqls` y `AutenticacionController.java` |
| `POST /auth/verificar` en ms-establecimientos | `AuthController.java` (`/auth` + `/verificar`) y la llamada del gateway |
| Respuesta: usuarioId, nombre, establecimientoId, rol | record `UsuarioVerificado` |
| 4xx -> «Correo o clave incorrectos» | `AutenticacionController.java` |
| JWT HS256 emitido por el gateway; claims `sub`, `est`, `nom`, `rol`, `exp`; secreto >= 32 caracteres | `EmisorJwt.java` |
| Lectura de `Authorization: Bearer`; nunca rechaza, decide cada operación | `InterceptorSesion.java` |
| Exigen sesión: `inventario`, `resumenIngresos`, `notificaciones`, `clientesInactivos`, `cerrarCita`, y `cancelarCita` salvo `canceladaPor = "cliente"` | `Exigir.*` en `ConsultasController.java` |
