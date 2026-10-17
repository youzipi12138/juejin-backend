FROM eclipse-temurin:21-jre

RUN groupadd --system app \
    && useradd --system --gid app --uid 1001 app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=65", \
  "-XX:InitialRAMPercentage=25", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Duser.timezone=Asia/Shanghai", \
  "-jar", "/app/app.jar"]
