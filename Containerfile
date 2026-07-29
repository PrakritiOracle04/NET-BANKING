# Build all JARs on the Windows host first. This image uses only the already
# pulled Red Hat Java runtime, so Podman does not need Docker Hub.
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest

ARG SERVICE
WORKDIR /app

COPY ${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080 8081 8082 8083 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
