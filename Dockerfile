# Multi-stage build for Spring Boot application with Gradle

# Stage 1: Build the application
FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /app

# Copy Gradle files first (for better caching)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN gradle clean bootJar --no-daemon -x test

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to spring user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
