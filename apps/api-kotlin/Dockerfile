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
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# UseContainerSupport: respect Docker CPU/memory limits
# MaxRAMPercentage: use up to 75% of container memory for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
