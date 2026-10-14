# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY grpc-contract/pom.xml grpc-contract/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY grpc-contract/src/main grpc-contract/src/main
COPY inventory-service/src/main inventory-service/src/main

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl inventory-service -am -Dmaven.test.skip=true package \
    && cp inventory-service/target/*.jar /application.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /application.jar app.jar
USER app
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
