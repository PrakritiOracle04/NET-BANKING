# Build all JARs on the Windows host first. This image uses only the already
# pulled Red Hat Java runtime, so Podman does not need Docker Hub.
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest

ARG SERVICE
WORKDIR /app

COPY ${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


#------------------------------------------------------------------

# # Stage 1: compile the Maven multi-module project inside Podman.
# # The image is already available locally as ghcr.io/carlossg/maven:3.9.11-eclipse-temurin-21.
# FROM ghcr.io/carlossg/maven:3.9.11-eclipse-temurin-21 AS build

# WORKDIR /workspace
# COPY . .
# RUN mvn -DskipTests package

# # Stage 2: retain only the selected service JAR in the final runtime image.
# FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest

# ARG SERVICE
# WORKDIR /app
# COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar

# ENTRYPOINT ["java", "-jar", "/app/app.jar"]
