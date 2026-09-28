# Сборка и запуск в двух стадиях: в финальный образ попадает только jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Слой с зависимостями отдельный: правки кода не заставляют качать их заново
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

# curl нужен только для HEALTHCHECK: в jre-образе его нет
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 10001 app
USER app
WORKDIR /app

COPY --from=build /src/target/orderflow-0.1.0.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage вместо фиксированного -Xmx: контейнер ограничен памятью,
# и JVM должна знать свой предел
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
