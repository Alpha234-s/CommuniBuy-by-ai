# Build and test the JAR with ./gradlew build before building this runtime image.
ARG JAVA_IMAGE=eclipse-temurin:21.0.8_9-jre-jammy
FROM ${JAVA_IMAGE}
RUN groupadd --gid 10001 communibuy && useradd --uid 10001 --gid 10001 --no-create-home communibuy
WORKDIR /app
COPY --chown=10001:10001 app/build/libs/communibuy.jar /app/communibuy.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/communibuy.jar"]
