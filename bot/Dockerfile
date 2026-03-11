# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Cache dependencies layer — copy POM first, download deps, then copy source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S arknova && adduser -S arknova -G arknova

WORKDIR /app

# Copy built jar
COPY --from=build /build/target/arknova-bot-*.jar app.jar

# Runtime directories for generated images and card image cache
RUN mkdir -p /app/images /app/card-images && \
    chown -R arknova:arknova /app

USER arknova

EXPOSE 8080

# Enable virtual threads (Project Loom) and tune GC for container
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

# ── Dev stage (for docker-compose.dev.yml) ────────────────────────────────────
FROM build AS dev
WORKDIR /build
EXPOSE 8080 5005
ENTRYPOINT ["mvn", "spring-boot:run", \
    "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
