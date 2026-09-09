# syntax=docker/dockerfile:1

# =============================================================================
# Etapa 1: construcción
# -----------------------------------------------------------------------------
# Se usa la imagen oficial de Maven 3.9.16, la misma versión que fija el Maven
# Wrapper en .mvn/wrapper/maven-wrapper.properties. El wrapper no se ejecuta aquí
# porque su modo "only-script" descarga y descomprime la distribución en cada
# build, lo que exige unzip y una descarga extra dentro del contenedor.
# =============================================================================
FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /build

# El pom se copia solo para resolver dependencias. Al quedar en una capa aparte,
# Docker reutiliza la caché mientras el pom no cambie, aunque cambie el código.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src

# Las pruebas no corren aquí: CuentasApplicationTests levanta el contexto completo
# y necesita PostgreSQL. La verificación es ./mvnw.cmd test antes del PR.
RUN mvn -B -ntp -DskipTests package \
 && cp target/cuentas-*.jar target/cuentas.jar

# =============================================================================
# Etapa 2: ejecución
# -----------------------------------------------------------------------------
# Solo el JRE y el jar. No incluye Maven, código fuente ni caché de dependencias.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# El proceso no corre como root: si alguien escapa de la aplicación, no obtiene
# privilegios sobre el contenedor.
RUN addgroup --system cameia \
 && adduser --system --ingroup cameia cameia

COPY --from=build --chown=cameia:cameia /build/target/cuentas.jar /app/cuentas.jar

USER cameia

# Debe coincidir con server.port de application.properties.
ENV SERVER_PORT=8081
EXPOSE 8081

# start-period de 60s: el arranque de Spring Boot con JPA no cuenta como fallo.
# wget viene incluido en busybox, así que no se instala nada extra en la imagen.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --output-document=- "http://127.0.0.1:${SERVER_PORT}/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/cuentas.jar"]
