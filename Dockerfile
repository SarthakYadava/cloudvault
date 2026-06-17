FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM gradle:8.14.3-jdk21 AS backend-build
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew gradlew.bat ./
COPY src ./src
COPY --from=frontend-build /workspace/build/generated-resources/frontend ./src/main/resources/static

RUN gradle clean bootJar -PskipFrontend=true --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home cloudvault

COPY --from=backend-build --chown=cloudvault:cloudvault /workspace/build/libs/cloudvault-*.jar app.jar

USER cloudvault
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl --fail --silent "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
