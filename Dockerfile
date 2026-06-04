FROM gradle:8.14.3-jdk21-alpine AS build

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
COPY config ./config

RUN gradle --no-daemon --max-workers=1 clean test installDist

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/build/install/gem-os ./
COPY config ./config

ENV GEM_HOME=/app

CMD ["java", "-cp", "/app/lib/*", "com.onexeor.gemos.scheduler.SchedulerMainKt", "--status"]
