# RelayCRM API image. Built by Render from this repository (see docs/deploy/DEPLOY.md).
# The tests are not run here: CI runs them on every pull request before anything reaches main.

# --- 1. Build ---------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jdk-alpine AS build
WORKDIR /build

# Dependencies first, in their own layer: rebuilt only when pom.xml or the wrapper changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw --batch-mode --quiet dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --quiet -DskipTests package \
    && cp target/relaycrm-*.jar app.jar

# --- 2. Run -----------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jre-alpine

# Run as an unprivileged user, never as root.
RUN addgroup -S -g 10001 relay && adduser -S -u 10001 -G relay -H relay
WORKDIR /app
COPY --from=build --chown=relay:relay /build/app.jar app.jar
USER relay

# Sized for a 512 MB instance (Render's free tier):
#   MaxRAMPercentage=60     heap up to ~300 MB, leaving room for metaspace, threads and the JIT
#   UseSerialGC             the leanest collector; one CPU and a small heap do not need more
#   Xss512k                 smaller thread stacks (Tomcat starts a few dozen threads)
#   TieredStopAtLevel=1     quicker start and less memory for compiled code, at some peak speed
#   ExitOnOutOfMemoryError  crash and let the platform restart, rather than limp on
# JAVA_TOOL_OPTIONS can be overridden from the platform's environment settings.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# The app listens on $PORT (server.port in application.yml), 8080 when it is not set.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
