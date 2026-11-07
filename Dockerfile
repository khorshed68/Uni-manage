# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only the build descriptors first so dependency downloads are cached
# and not repeated when just the source code changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- Stage 2: minimal runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user, not root.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
COPY --from=build /workspace/target/ums.jar app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
