# Build stage (Debian-based: fewer registry/cache issues than Alpine on some hosts)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app && mkdir -p /app/uploads && chown -R app:app /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 9900
ENV FILE_UPLOAD_PATH=/app/uploads
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
