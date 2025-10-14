# ====================================================================================================
# Stage 1: Builder
# ====================================================================================================
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Layer 1: Copy Gradle wrapper and settings (rarely changes)
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts ./

# Layer 2: Copy build configuration and download dependencies (changes occasionally)
COPY build.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon

# Layer 3: Copy source code (changes frequently)
COPY src/ src/

# Build Fat JAR with all dependencies
RUN gradle buildFatJar --no-daemon

# ====================================================================================================
# Stage 2: Runtime
# ====================================================================================================
FROM amazoncorretto:17-alpine

LABEL maintainer="PocketWardrobe Team"
LABEL description="PocketWardrobe Backend - Ktor REST API"

WORKDIR /app

# Install curl for health checks (optional but useful)
RUN apk add --no-cache curl

# Create non-root user and group for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy Fat JAR from builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Create directories for uploads and looks
RUN mkdir -p uploads looks

# Set ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# JVM optimizations for container environment
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check (optional, uncomment if needed)
# HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
#   CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
