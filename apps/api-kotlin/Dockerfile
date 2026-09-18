# Stage 1: Build the JAR
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Cache dependencies before copying source
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q

# Build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -q

# Stage 2: Runtime image (JRE only — smaller)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# UseContainerSupport: respect Docker CPU/memory limits
# MaxRAMPercentage: use up to 75% of container memory for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
