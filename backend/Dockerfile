FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY frontend/*.html frontend/
COPY frontend/css frontend/css
COPY frontend/js frontend/js
COPY frontend/vendor frontend/vendor
COPY frontend/favicon.svg frontend/favicon.svg
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 msa && useradd --uid 10001 --gid msa --create-home msa
WORKDIR /app
COPY --from=build --chown=msa:msa /workspace/backend/target/markov-stock-analyzer-0.1.0-SNAPSHOT.jar /app/application.jar
USER msa
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
