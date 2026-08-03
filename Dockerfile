# syntax=docker/dockerfile:1
#
# 멀티모듈 백엔드용 단일 Dockerfile. 어느 모듈을 이미지로 만들지 MODULE 인자로 고른다.
#
#   docker build --build-arg MODULE=module-api -t battery-backend .
#   docker build --build-arg MODULE=module-ai  -t battery-backend-ai .
#
# module-core 는 라이브러리라 bootJar 가 꺼져 있다. 이미지로 만들지 않는다.
#
# 포트: module-api = 8080 (FE 대응) · module-ai = 8081 (모델 서버 연동)

ARG MODULE=module-api

# --- 빌드 -------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 스크립트를 먼저 복사한다. 소스만 바뀌었을 때 의존성 내려받기를 건너뛰기 위함이다.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY module-core/build.gradle module-core/
COPY module-api/build.gradle  module-api/
COPY module-ai/build.gradle   module-ai/

# 의존성 미리 해소. 실패해도 빌드를 막지 않는다(캐시 목적일 뿐이다).
RUN chmod +x gradlew \
 && ./gradlew --no-daemon :module-api:dependencies :module-ai:dependencies \
      --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY module-core/src module-core/src
COPY module-api/src  module-api/src
COPY module-ai/src   module-ai/src

ARG MODULE
# bootJar 는 실행 가능한 jar 를, jar 는 -plain.jar 를 만든다.
# *-SNAPSHOT.jar 패턴은 -plain.jar 를 잡지 않으므로 실행 jar 만 골라진다.
RUN ./gradlew --no-daemon :${MODULE}:bootJar -x test \
 && cp ${MODULE}/build/libs/*-SNAPSHOT.jar /app.jar

# --- 실행 -------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# 루트로 돌리지 않는다.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER app

# 문서용 표기다. 실제 노출은 compose 와 nginx 가 정한다.
# module-api 는 8080, module-ai 는 8081 을 듣는다.
EXPOSE 8080 8081

# 힙 상한은 JAVA_TOOL_OPTIONS 로 바깥에서 준다(계약 [실행/환경변수]).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
