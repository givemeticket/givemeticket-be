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
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.net,jdk.charsets,jdk.unsupported,jdk.zipfs \
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
RUN mkdir -p /logs/dumps && chown -R app:app /logs /app
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:InitialRAMPercentage=70.0 \
-XX:MaxRAMPercentage=70.0 \
-XX:MetaspaceSize=128m \
-XX:MaxMetaspaceSize=256m \
-XX:+UseG1GC \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/logs/dumps \
-XX:ErrorFile=/logs/hs_err_pid%p.log \
-Xlog:gc=info:file=/logs/gc.log:time,uptime:filecount=3,filesize=5M"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
