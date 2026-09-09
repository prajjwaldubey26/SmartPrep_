# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package \
    && cp target/smartprep-backend-*.jar /app/app.jar

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd -r -u 1001 spring \
    && chown -R spring:spring /app
USER spring

COPY --from=build /app/app.jar /app/app.jar

ENV JAVA_OPTS=""
ENV PORT=8080
EXPOSE 8080

# Render sets PORT; Spring Boot reads server.port unless overridden
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
