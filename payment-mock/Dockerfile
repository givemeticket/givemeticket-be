# ---- build ----
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ---- jre ----
FROM eclipse-temurin:21-jdk-alpine AS jre
RUN jlink \
      --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.xml,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.management.agent,jdk.net,jdk.charsets,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output /javaruntime

# ---- run ----
FROM alpine:3.20
RUN apk add --no-cache tzdata wget && \
    addgroup -S app && adduser -S app -G app
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre /javaruntime ${JAVA_HOME}
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
