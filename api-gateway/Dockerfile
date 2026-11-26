# ---------- Étape 1 : build (JDK + Maven Wrapper) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dépendances d'abord (couche mise en cache tant que le pom ne change pas)
COPY mvnw pom.xml ./
COPY .mvn .mvn
# 3 essais (-U ensuite : Maven ne garde pas l'échec en cache) : un téléchargement interrompu depuis Maven Central
# (réseau instable, 6 builds en parallèle) ne doit pas faire échouer tout docker compose up --build
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && (./mvnw -B -q dependency:go-offline \
    || (sleep 5 && ./mvnw -B -q -U dependency:go-offline) || (sleep 15 && ./mvnw -B -q -U dependency:go-offline))

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    || (sleep 5 && ./mvnw -B -q -U package -DskipTests) || (sleep 15 && ./mvnw -B -q -U package -DskipTests)

# ---------- Étape 2 : exécution (JRE seul) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/api-gateway-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
