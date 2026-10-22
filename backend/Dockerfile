# Immagine del backend per Render (runtime Docker: Render non ha un runtime Java nativo).
# Build in due fasi: la prima compila con il JDK e il Maven wrapper, la seconda contiene solo il JRE e il jar.
# I test non girano qui (servirebbe un PostgreSQL): vanno eseguiti in locale con "./mvnw test" prima del push.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# Prima solo wrapper e pom: le dipendenze restano in cache finché il pom non cambia.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package && cp target/gestionale-*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
# Il processo non gira come root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
# Render indica la porta con la variabile PORT (letta da application.yml); 8080 è solo il valore di riserva.
EXPOSE 8080
# La JVM usa al massimo il 75% della memoria del container (il piano gratuito di Render ne ha poca).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
