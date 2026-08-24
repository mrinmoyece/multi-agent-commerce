# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:22-jdk-jammy@sha256:d8e6ba486df17bf758888d2b1b608133d1eedca8daf69d3fc6bf78d8be81e07e AS builder
WORKDIR /build

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.lockfile ./

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew dependencies --no-daemon --quiet

COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:22-jre-jammy@sha256:dbcae8b5dd4d63f81739a538ec2c09797735f04a21d814f9071b62f018326043 AS runtime

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="multi-agent-commerce"
LABEL org.opencontainers.image.description="Multi-agent e-commerce intelligence platform"
LABEL org.opencontainers.image.vendor="AgentForge"
LABEL org.opencontainers.image.source="https://github.com/mrinmoyece/multi-agent-commerce"
LABEL org.opencontainers.image.revision="${VCS_REF}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

RUN groupadd --gid 10001 appgroup \
 && useradd --uid 10001 --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser \
 && apt-get update \
 && apt-get install --yes --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
