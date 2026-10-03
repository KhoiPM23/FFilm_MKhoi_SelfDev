# =============================================================
# FFilm Production Dockerfile
# Build context: /project (cd project/ before docker build)
# =============================================================

# ---- Build stage ----
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Cache Maven dependencies first
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q || true

# Build application (skip tests for faster CI deploy)
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

# JVM tuning for Render free tier (512MB RAM limit)
# -Xmx400m  : max heap 400MB (leaves ~100MB for OS/non-heap)
# -Xss512k  : reduce thread stack (saves memory)
# -XX:+UseG1GC : better for low-memory containers
ENV JAVA_OPTS="-Xmx400m -Xms128m -Xss512k -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java \ -Dspring.profiles.active=prod -jar app.jar"]
