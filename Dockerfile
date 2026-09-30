# syntax=docker/dockerfile:1
# --platform=$BUILDPLATFORM : l'étape Maven tourne UNE fois, en natif sur la machine de build.
# Un JAR ne dépend pas de l'architecture ; sans cette option, le build arm64 exécuterait Maven
# sous émulation QEMU (plus de 10 min par service).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY --chmod=755 mvnw ./
COPY pom.xml ./
COPY libs/ libs/
COPY services/ services/
ARG SERVICE=catalogue-service
# Cache Maven entre deux builds (BuildKit)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -pl services/${SERVICE} -am -DskipTests -Djacoco.skip=true \
 && java -Djarmode=tools -jar services/${SERVICE}/target/${SERVICE}.jar \
         extract --layers --launcher --destination /extracted

# distroless : ni shell, ni gestionnaire de paquets ; utilisateur non-root.
# Seule cette étape varie selon l'architecture cible (amd64 / arm64).
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 65532:65532
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
            "org.springframework.boot.loader.launch.JarLauncher"]
