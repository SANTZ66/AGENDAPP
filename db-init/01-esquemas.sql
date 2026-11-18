-- Se ejecuta automaticamente la primera vez que arranca el contenedor de
-- PostgreSQL (carpeta docker-entrypoint-initdb.d).
--
-- Crea un esquema independiente por servicio. Cada servicio es propietario
-- exclusivo del suyo: ningun servicio consulta las tablas de otro, y toda
-- consulta cruzada se resuelve por API REST o por evento (seccion 7.3).

CREATE SCHEMA IF NOT EXISTS establecimientos;
CREATE SCHEMA IF NOT EXISTS agenda;
CREATE SCHEMA IF NOT EXISTS operaciones;
CREATE SCHEMA IF NOT EXISTS relacion_cliente;
