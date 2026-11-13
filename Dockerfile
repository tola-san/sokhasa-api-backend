# Build with the same Java release configured in pom.xml.
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
