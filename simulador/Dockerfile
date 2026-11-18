# ---- Etapa 1: compilar el jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Etapa 2: imagen final ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/simulador-0.1.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
