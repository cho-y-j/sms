# Multi-stage build for BizConnect Server V2
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy build configuration
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY gradle.properties .
COPY server server

# Create a settings file that only includes the server module
RUN echo 'pluginManagement {\n\
    repositories {\n\
        google()\n\
        mavenCentral()\n\
        gradlePluginPortal()\n\
    }\n\
}\n\
dependencyResolutionManagement {\n\
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n\
    repositories {\n\
        google()\n\
        mavenCentral()\n\
    }\n\
}\n\
rootProject.name = "BizConnect"\n\
include(":server")' > settings.gradle.kts

# Create a root build.gradle.kts that only has server plugins
RUN echo 'plugins {\n\
    alias(libs.plugins.kotlin.jvm) apply false\n\
    alias(libs.plugins.kotlin.serialization) apply false\n\
}' > build.gradle.kts

# Build application
RUN chmod +x gradlew && \
    ./gradlew :server:shadowJar --no-daemon && \
    ls -la server/build/libs/

# Runtime image with minimal attack surface
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install minimal runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
    useradd -m -u 1000 bizconnect && \
    mkdir -p /var/log/bizconnect && \
    chown bizconnect:bizconnect /var/log/bizconnect

COPY --from=builder /app/server/build/libs/server-all.jar server.jar

# Set secure permissions
RUN chmod 600 server.jar && \
    chown bizconnect:bizconnect server.jar

# Environment variables (override via docker-compose)
ENV SERVER_PORT=8080 \
    ENVIRONMENT=production \
    DB_HOST=postgresql \
    DB_PORT=5432 \
    DB_NAME=bizconnect \
    DB_USER=bizconnect_user

EXPOSE 8080

# Security: run as non-root user
USER bizconnect

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run with security flags
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+DisableExplicitGC", \
    "-Dfile.encoding=UTF-8", \
    "-Djava.security.egd=file:/dev/urandom", \
    "-Dserver.shutdown=graceful", \
    "-jar", "server.jar"]
