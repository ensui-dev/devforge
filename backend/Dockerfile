# Build the jar in one stage so the runtime image carries no Maven, no sources,
# and no build cache.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copy the wrapper and POM first: dependency resolution is then cached and only
# re-runs when the POM itself changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests need Docker (Testcontainers), which is not available inside a build, so
# they run in CI instead.
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run unprivileged: nothing in the service needs root.
RUN addgroup -S devforge && adduser -S devforge -G devforge
USER devforge

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Actuator's liveness probe, so an orchestrator can tell "started" from "ready".
# Addressed by IP rather than by name for the same reason as the frontend: inside
# a container `localhost` may resolve to ::1 before IPv4.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

# `exec` form so the JVM is PID 1 and receives SIGTERM directly, giving Spring a
# chance to shut down cleanly.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
