# ---- Etapa 1: compilar el jar ----
# Usa una imagen con Maven y JDK 21 para construir sin depender del equipo local.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primero solo el pom, para cachear la descarga de dependencias.
COPY pom.xml .
RUN mvn -q dependency:go-offline
# Ahora el codigo y el empaquetado.
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Etapa 2: imagen final, solo el jar y el runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ms-agenda-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
