# Click Checker

Click Checker는 조직 단위 이벤트 수집과 분석을 제공하는 멀티테넌트 제품 분석 서비스입니다.

이 저장소는 단순 CRUD 데모가 아니라,

- 제품 이벤트 적재 API
- 공개 분석 API
- JWT 기반 관리자 콘솔
- 제품 API 가이드
- 운영 배포, 관측, 성능 검증

까지 하나의 서비스로 연결해 본 포트폴리오 프로젝트입니다.

운영 주소:

- 서비스: [https://clickchecker.dev](https://clickchecker.dev)
- Grafana: [https://grafana.clickchecker.dev](https://grafana.clickchecker.dev)
  - Id : amdin
  - Pw : admin

## 이 프로젝트에서 보여주려는 것

- 관리자 콘솔 인증과 제품 API 인증을 분리한 보안 경계 설계
- organization 기준 멀티테넌트 데이터 격리
- raw event 저장 + route/event type 정규화
- frontend + backend 분리 배포와 same-origin 공개 진입 구조
- backend Blue/Green, frontend single container 운영 전략
- Prometheus, Grafana, Loki, Sentry를 포함한 관측 체계
- k6 기반 부하 테스트와 성능 개선 과정 문서화

## 핵심 기능

### 1. 이벤트 적재
- `POST /api/events`
- `X-API-Key` 기반 organization 스코프 인증
- `eventType`, `path`, `occurredAt`, `externalUserId`, `payload` 지원

### 2. 분석 조회
- overview
- routes / event types
- trends
- users
- activity
- retention
- funnels

### 3. 관리자 콘솔
- 회원가입 / 로그인 / 토큰 재발급
- organization 생성
- API key 확인 / 재발급
- route template 관리
- event type mapping 관리
- 멤버 관리

### 4. 제품 API 가이드
- 소개
- 빠른 시작
- API Key 가이드
- 이벤트 전송 가이드
- 데이터 정규화 가이드
- 집계 API 가이드

### 5. 데모 흐름
- 고정 ID `99999` demo organization 운영
- Quick Start
- 데모 조직 참여
- 샘플 이벤트 적재 확인

## 서비스 흐름

```text
signup / login
-> organizations
-> organization 생성 또는 demo 참여
-> API key 확인
-> 이벤트 전송
-> overview / 상세 분석 확인

guide
-> quick-start
-> api-key-guide
-> send-events
-> data-mapping
-> analytics-api
```

## 설계 요약

### 인증 경계

- 관리자 콘솔
  - `Authorization: Bearer <JWT>`
  - `/api/v1/admin/**`
- 제품 API
  - `X-API-Key`
  - `POST /api/events`
  - `GET /api/v1/events/analytics/**`

즉 사람용 인증과 제품 연동용 인증을 분리했습니다.

### 멀티테넌트 경계

- 최상위 경계는 `Organization`
- 관리자 콘솔은 `Account -> OrganizationMember`로 접근 범위를 제한
- 제품 API는 `X-API-Key` 인증 결과로 organization을 확정

### 데이터 모델

핵심 엔티티:

- `Organization`
- `Account`
- `RefreshToken`
- `OrganizationMember`
- `Event`
- `EventUser`
- `RouteTemplate`
- `EventTypeMapping`
- `EventHourlyRollup`
- `EventRollupWatermark`

### 정규화 전략

- raw path는 `RouteTemplate`으로 `routeKey`에 매핑
- raw event type은 `EventTypeMapping`으로 `canonicalEventType`에 매핑
- 매칭되지 않는 값은 `UNMATCHED_ROUTE`, `UNMAPPED_EVENT_TYPE`으로 드러남

즉 원본 이벤트를 유지하면서 분석 화면에서는 읽기 좋은 공통 키를 제공합니다.

## 현재 아키텍처

```text
Browser
-> https://clickchecker.dev
-> Nginx
   -> /api/**, /actuator/** -> active backend (blue or green)
   -> /, /guide, /dashboard/**, /_next/**, /healthz -> frontend
```

배포 기준:

- frontend: Next.js standalone single container
- backend: Spring Boot Blue/Green
- DB: Amazon RDS for PostgreSQL
- 공개 진입점: Nginx + HTTPS
- 배포: GitHub Actions + ECR + S3 + CodeDeploy
- 관측: Prometheus + Grafana + Loki + Promtail + Alertmanager + Sentry

## 기술 스택

### Backend
- Java 21
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- Querydsl
- Flyway
- PostgreSQL

### Frontend
- Next.js 16 App Router
- React 19
- Mantine
- Recharts

### Infra / Ops
- Docker / Docker Compose
- Nginx
- GitHub Actions
- AWS ECR
- AWS S3
- AWS CodeDeploy
- Amazon RDS for PostgreSQL

### Observability / Performance
- Spring Actuator
- Micrometer
- Prometheus
- Grafana
- Loki / Promtail
- Alertmanager
- Sentry
- k6

### AI
- Codex
- ChatGPT

## 로컬 실행

### 1. PostgreSQL 실행

```bash
docker compose up -d postgres
```

### 2. Backend 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 주소:

- backend: `http://localhost:8080`

### 3. Frontend 실행

```bash
cd frontend
npm install
npm run dev
```

기본 주소:

- frontend: `http://localhost:3001`

### 4. Demo organization 데이터 생성

루트 디렉터리에서:

```bash
bash scripts/data/seed-demo-organization.sh
```

## 배포 요약

운영 배포는 `prod` 브랜치 push 기준 자동화되어 있습니다.

흐름:

```text
push to prod
-> GitHub Actions
-> backend / frontend image build
-> ECR push
-> S3 bundle upload
-> CodeDeploy
-> EC2 hook
   -> backend blue/green deploy
   -> frontend recreate
   -> nginx config apply
   -> health / smoke verify
```

## 테스트와 검증

### Backend

```bash
./gradlew test
./gradlew postgresTest
```

### Frontend

```bash
cd frontend
npm run build
```

### Docker

```bash
docker build -t click-checker-app:test .
docker build -f frontend/Dockerfile -t click-checker-frontend:test ./frontend
```

## 문서

서비스와 설계/운영 문서는 `docs/`에 정리돼 있습니다.

### 서비스 / 도메인 / API
- [서비스 소개](docs/01-서비스-개요/서비스-소개.md)
- [도메인 모델 개요](docs/02-도메인-설계/01-도메인-모델-개요.md)
- [관리자 콘솔 도메인 경계](docs/02-도메인-설계/02-관리자-콘솔-도메인-경계.md)
- [API Key 인증 정책](docs/03-API-설계/01-api-key-인증-정책.md)
- [프런트엔드 진입 및 same-origin 정책](docs/03-API-설계/08-프런트엔드-진입-및-same-origin-정책.md)

### 프런트 / 가이드
- [프런트엔드 계획](docs/07-front/01-프론트엔드-계획.md)
- [화면 흐름](docs/07-front/02-화면-흐름.md)
- [빠른 시작](docs/08-사용-가이드/01-빠른-시작.md)
- [데이터 정규화](docs/08-사용-가이드/04-데이터-정규화.md)
- [집계 API 가이드](docs/08-사용-가이드/05-집계-api.md)

### 배포 / 운영
- [배포 개요](docs/05-배포/01-배포-개요.md)
- [프런트엔드 배포 런북](docs/05-배포/38-프런트엔드-배포-런북.md)
- [프런트엔드 배포 트러블슈팅](docs/05-배포/39-프런트엔드-배포-트러블슈팅.md)
- [프런트엔드 배포 종합](docs/05-배포/40-프런트엔드-배포-종합.md)

### 성능 개선
- [성능 개선 종합](docs/06-성능-개선/07-성능-개선-종합.md)
- [성능 개선 최종 정리](docs/06-성능-개선/08-성능-개선-최종-정리.md)

## 이 프로젝트를 통해 정리한 것

- 제품 연동 API와 관리자 콘솔 API는 인증과 책임을 분리해야 한다.
- 멀티테넌트 서비스는 organization 스코프를 모든 계층에서 일관되게 강제해야 한다.
- raw event를 유지하면서도, 분석 화면에는 정규화된 키를 제공해야 읽을 수 있다.
- read-heavy 분석 서비스는 캐시와 rollup을 무작정 늘리기보다 실제 병목 축을 먼저 확인해야 한다.
- frontend와 backend를 같은 도메인으로 묶으면 제품 경험은 단순해지지만, 경로 분기와 운영 책임은 더 명확히 나눠야 한다.

## 상태 요약

현재 Click Checker는:

- 관리자 계정 회원가입/로그인
- organization 생성/멤버 관리
- 제품 API key 발급/재발급
- 이벤트 적재 API
- overview / activity / users / retention / funnels 분석
- 제품 API 가이드
- frontend + backend 운영 배포

까지 실제로 연결된 상태입니다.
