# ---------- Étape 1 : build (JDK + Maven Wrapper) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dépendances d'abord (couche mise en cache tant que le pom ne change pas)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Sources, dont src/main/proto : protoc (binaire Linux choisi par os-maven-plugin) génère les stubs
COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- Étape 2 : exécution (JRE seul) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/telemetrie-service-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
