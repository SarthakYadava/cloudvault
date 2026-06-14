FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew gradlew.bat ./
COPY src ./src

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 cloudvault
COPY --from=build /workspace/build/libs/cloudvault-*.jar app.jar

USER cloudvault
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
