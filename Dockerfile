# ═══════════════════════════════════════════════════════════
#  Dockerfile - juribook-auth-service
#  Multi-stage build : build Maven → runtime JRE
# ═══════════════════════════════════════════════════════════

# ── Étape 1 : Build avec Maven ────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copier le pom.xml en premier pour profiter du cache Docker :
# les dépendances Maven ne sont re-téléchargées que si pom.xml change
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et compiler
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Étape 2 : Runtime avec JRE ───────────────────────────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copier uniquement le JAR généré (pas les sources ni Maven)
COPY --from=build /app/target/*.jar app.jar

# Port exposé par l'auth-service
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]