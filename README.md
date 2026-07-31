# KT AIVLE Big Project Backend

KT AIVLE 9기 빅프로젝트 16조의 이차전지 셀 CT/RGB 이미지 기반 AI 결함 검사 플랫폼 백엔드입니다.

Spring Boot 기반 멀티 모듈 프로젝트이며, 프론트엔드 API, 공통 도메인, AI 서버 연동, LLM 리포트 연동을 분리해 관리합니다.

## Project Structure

```text
big-project/
├── module-core/   # Entity, Enum, Repository 등 공통 도메인 모듈
├── module-api/    # React 프론트엔드와 통신하는 API 서버 / 개별/일일 리포트 및 LLM 연동 모듈
├── module-ai/     # AI 추론 서버 연동 모듈
X(삭제) module-llm/    # 개별/일일 리포트 및 LLM 연동 모듈
```

### Module Roles

| Module | Role | Default port |
| --- | --- | ---: |
| `module-core` | JPA Entity, Repository, Enum 등 공통 코드 | - |
| `module-api` | 인증, 시뮬레이션, 배터리, 대시보드 API | `8080` |
| `module-ai` | AI 추론 서버 통신 및 분석 결과 처리 | `8081` |
| `module-llm` | LLM 서버 통신 및 리포트 생성 | `8082` |

`module-core`는 실행 서버가 아닌 라이브러리 모듈입니다. `module-api`, `module-ai`, `module-llm`은 독립적인 Spring Boot 애플리케이션으로 실행할 수 있도록 구성되어 있습니다.

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Gradle 8.14.x
- Spring Data JPA
- Spring Security + JWT
- PostgreSQL
- Redis
- Springdoc OpenAPI / Swagger UI
- MinIO 또는 AWS S3 호환 Object Storage

## Requirements

- JDK 17 이상
- Docker Desktop 또는 PostgreSQL, Redis 실행 환경
- Git
- IntelliJ IDEA 권장

Java 버전을 확인합니다.

```powershell
java -version
.\gradlew.bat --version
```

두 명령 모두 Java 17 이상을 사용해야 합니다.

## Configuration

비밀번호, JWT secret, 외부 서버 주소 등 민감한 값은 저장소에 커밋하지 않습니다. 로컬 실행 시 환경변수 또는 로컬 전용 설정 파일을 사용하세요.

필요한 주요 설정 예시는 다음과 같습니다.

```text
DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USERNAME=<username>
DB_PASSWORD=<password>
REDIS_HOST=localhost
REDIS_PORT=6379
AI_BASE_URL=http://localhost:8081
LLM_BASE_URL=http://localhost:8082
JWT_SECRET=<strong-secret>
```

현재 저장소의 `application.yml`에 DB 접속 정보가 직접 포함되어 있다면, 실행 전 비밀번호를 교체하고 환경변수 방식으로 변경해야 합니다.

## Build and Test

프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat clean build
```

테스트만 실행하려면 다음 명령을 사용합니다.

```powershell
.\gradlew.bat test
```

특정 모듈만 빌드할 수 있습니다.

```powershell
.\gradlew.bat :module-api:build
.\gradlew.bat :module-ai:build
.\gradlew.bat :module-llm:build
```

## Run Applications

각 모듈은 IntelliJ에서 다음 Application 클래스를 실행할 수 있습니다.

| Application | Module | Port |
| --- | --- | ---: |
| `ApiApplication` | `module-api` | `8080` |
| `AiApplication` | `module-ai` | `8081` |
| `LlmApplication` | `module-llm` | `8082` |

Gradle로 실행할 때는 모듈별 `bootRun`을 사용합니다.

```powershell
.\gradlew.bat :module-api:bootRun
.\gradlew.bat :module-ai:bootRun
.\gradlew.bat :module-llm:bootRun
```

## API Documentation

`module-api` 실행 후 Swagger UI에서 API를 확인할 수 있습니다.

```text
http://localhost:8080/swagger-ui/index.html
```

주요 API 영역은 다음과 같습니다.

- 인증: 회원가입, 로그인, 이메일 인증
- 시뮬레이션: 시뮬레이션 시작/조회/정지
- 배터리: 배터리 셀 목록 및 상세 조회
- 검사: 검사 결과 및 결함 정보 조회
- 대시보드: 검사 현황 및 통계 조회
- 리포트: 개별·일일 리포트 생성 및 조회

세부 요청/응답 형식은 팀의 CONTRACT SSOT와 API 명세서를 기준으로 합니다.

## Development Flow

작업 전 최신 `main`을 기준으로 기능 브랜치를 생성합니다.

```powershell
git switch main
git pull origin main
git switch -c feature/<feature-name>
```

작업 후에는 테스트와 빌드를 확인하고 Pull Request를 생성합니다.

```powershell
.\gradlew.bat clean build
git add .
git commit -m "feat: <change-description>"
git push -u origin feature/<feature-name>
```

브랜치명 예시:

```text
feature/auth
feature/simulation
feature/inspection
feature/ai-integration
feature/report
```

## Development Rules

1. DB 구조와 API 요청/응답은 CONTRACT SSOT를 기준으로 구현합니다.
2. Entity 필드나 Enum을 변경할 때는 관련 API와 프론트엔드 타입을 함께 확인합니다.
3. `module-core` 변경은 다른 모듈에 영향을 줄 수 있으므로 작업 전에 팀에 공유합니다.
4. 비밀번호, JWT secret, API key 등 민감 정보는 커밋하지 않습니다.
5. 기능 단위로 작은 커밋을 만들고 Pull Request에서 변경 범위를 명확히 합니다.
6. AI 및 LLM 연동은 외부 서버 주소를 코드에 하드코딩하지 않고 환경변수로 관리합니다.

## Domain Flow

```text
React Frontend
    │
    ▼
module-api
    │
    ├── PostgreSQL / Redis / MinIO
    ├── module-ai ── FastAPI AI Server
    └── module-llm ── FastAPI LLM Server
```

검사 업무 흐름은 다음과 같습니다.

```text
시뮬레이션 시작
→ 검사 배치 및 이미지 등록
→ AI 이미지 분석
→ defect_result 저장
→ 이미지 결과를 셀 단위로 집계
→ 대시보드 및 리포트 제공
```
