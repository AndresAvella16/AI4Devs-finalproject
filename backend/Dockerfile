# syntax=docker/dockerfile:1
# Imagen del backend (JP-OPS-01). Build multi-stage: Maven Wrapper con JDK 21 → JRE 21.
# Los tests no se ejecutan aquí: los ejecuta la CI con ./mvnw verify.

# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primero solo el wrapper y el pom: la capa de dependencias se reutiliza mientras no cambie el pom.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/jobpilot-backend-*.jar /workspace/app.jar

# ---- Runtime ----
FROM eclipse-temurin:21-jre

# El HEALTHCHECK usa curl. La imagen base lo trae hoy, pero no lo garantiza: si falta, se instala.
RUN command -v curl >/dev/null \
    || (apt-get update \
        && apt-get install -y --no-install-recommends curl \
        && rm -rf /var/lib/apt/lists/*)

RUN groupadd --system jobpilot && useradd --system --gid jobpilot --no-create-home jobpilot
WORKDIR /app
COPY --from=build --chown=jobpilot:jobpilot /workspace/app.jar app.jar
USER jobpilot

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Europe/Madrid"

# Flyway aplica las migraciones al arrancar: se da margen antes de contar los fallos.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
