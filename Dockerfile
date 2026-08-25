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
    addgroup -S app && adduser -S app -G app && \
    mkdir -p /logs/dumps && chown -R app:app /logs
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre /javaruntime ${JAVA_HOME}
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# 힙 크기와 GC 종류는 따로 떼어 둔다. 실험할 때 이미지를 다시 굽지 않고
# 컨테이너 환경변수만 바꿔서 (docker compose up -d backend) 비교할 수 있게 하기 위해서다.
# 예) JVM_HEAP_OPTS="-Xms512m -Xmx512m"  JVM_GC_OPTS="-XX:+UseParallelGC"
ENV JVM_HEAP_OPTS="-XX:InitialRAMPercentage=70.0 -XX:MaxRAMPercentage=70.0"
ENV JVM_GC_OPTS="-XX:+UseG1GC"
# 한 번에 하나씩 얹어 보는 튜닝 플래그용. 기본은 비어 있다.
ENV JAVA_OPTS_EXTRA=""
# 아래는 실험 대상이 아니라 항상 켜 두는 진단 옵션이다.
#   gc.log        : GC 한 건당 한 줄. Loki 로도 흘려보내 Grafana 에서 같이 본다.
#   gc-detail.log : gc* 전체 태그. 힙 영역별 증감과 pause 단계까지 남는다.
ENV JVM_DIAG_OPTS="-XX:MetaspaceSize=128m \
-XX:MaxMetaspaceSize=256m \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/logs/dumps \
-XX:ErrorFile=/logs/hs_err_pid%p.log \
-Xlog:gc:file=/logs/gc.log:time,uptime,level,tags:filecount=3,filesize=5M \
-Xlog:gc*=info:file=/logs/gc-detail.log:time,uptime,level,tags:filecount=5,filesize=10M"
ENTRYPOINT ["sh", "-c", "exec java $JVM_HEAP_OPTS $JVM_GC_OPTS $JVM_DIAG_OPTS $JAVA_OPTS_EXTRA -jar /app/app.jar"]
