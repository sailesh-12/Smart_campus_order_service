# syntax=docker/dockerfile:1
# Multi-stage build. Base images are pinned to exact versions; bump them deliberately (Trivy gates the result).

FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21.0.10_7-jre-noble
WORKDIR /app
# Fixed numeric non-root user, so Kubernetes can enforce runAsNonRoot
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
# Layered copy: dependencies change less often than application classes
COPY --from=build --chown=10001:10001 /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
# Container-aware heap; /tmp is a writable emptyDir in Kubernetes (root filesystem can stay read-only)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
