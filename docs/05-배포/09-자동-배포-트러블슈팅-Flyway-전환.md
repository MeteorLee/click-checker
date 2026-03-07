# 자동 배포 트러블슈팅 09 - Flyway 전환 이슈

## 1. 목적
- Flyway 전환 과정에서 실제로 발생한 장애와 해결 방법을 기록한다.
- 동일 이슈 재발 시 빠르게 복구할 수 있도록 기준 절차를 남긴다.

---

## 2. 이슈 1 - PostgreSQL 16 인식 실패

### 증상
- 앱 기동 시 Flyway 초기화 실패
- 로그: `Unsupported Database: PostgreSQL 16.x`

### 원인
- `flyway-core`만 사용하여 PostgreSQL 16 인식 모듈이 누락됨

### 조치
- `build.gradle`에 `org.flywaydb:flyway-database-postgresql` 추가

### 결과
- Flyway가 PostgreSQL 16.x를 정상 인식하고 migration 실행 가능

---

## 3. 이슈 2 - baseline 없이 기존 운영 DB에 migration 적용 실패

### 증상
- `flyway_schema_history`가 없는 기존 DB에서 migration 적용 중 실패

### 원인
- 기존 운영 DB는 이미 스키마가 존재하는데 baseline 없이 migration을 적용하려고 함

### 조치
- 운영 DB 최초 전환 시 1회 baseline 적용
  - `baselineOnMigrate=true`
  - `baselineVersion=1`
- baseline 후 `V2~V9` 순차 적용

### 결과
- `flyway_schema_history`에 version `1~9` 성공 기록 확인

---

## 4. 이슈 3 - 로컬 H2 CI 경로에서 Flyway SQL 실패

### 증상
- `./gradlew test` 실패
- H2에서 PostgreSQL 전용 SQL 실행 오류

### 원인
- CI fast lane(H2)에서 Flyway가 활성화되어 Postgres 전용 migration을 실행함

### 조치
- `src/test/resources/application-ci.yml`에 `spring.flyway.enabled=false` 적용

### 결과
- H2 fast CI는 기존 방식(ddl-auto create-drop)으로 통과
- Postgres 경로는 `postgresTest`에서 별도 검증 유지

---

## 5. 이슈 4 - API_KEY_PEPPER 환경변수 키 불일치

### 증상
- 앱 기동 실패
- 로그: `API_KEY_PEPPER must not be blank`

### 원인
- compose에 `APP_API_KEY_PEPPER`로 주입되어 애플리케이션 설정 키와 불일치

### 조치
- `docker-compose.yml` 환경변수 키를 `API_KEY_PEPPER` 기준으로 통일

### 결과
- 앱 정상 기동 및 health `UP`

---

## 6. 운영 적용 체크
- DB 백업 후 진행
- baseline 1회 적용 후 옵션 제거
- `/actuator/health` 확인
- `flyway_schema_history` 버전/성공 여부 확인

---

## 7. 이슈 5 - compose 기동 실패 시 롤백 미실행

### 증상
- 배포 로그에서 `dependency failed to start: container click-checker-app is unhealthy` 후 즉시 종료
- `git pull`은 반영되었지만 `Start rollback to ...` 로그가 없음

### 원인
- 배포 스크립트가 `set -e`로 동작하여 `compose_up` 실패 시 즉시 종료
- 기존 롤백 호출 지점은 health/smoke 검증 블록에만 존재

### 조치
- `deploy-prod.yml`에 `compose_up` 실패 분기 추가:
  - 실패 시 로그 덤프 후 `rollback()` 실행
- 배포 시작 전 `.env` 필수 값 사전 검증 추가:
  - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `API_KEY_PEPPER`, `API_KEY_ENV`

### 결과
- `compose_up` 단계 실패도 자동 롤백 경로로 진입
- 환경변수 누락은 배포 초기에 빠르게 실패하여 원인 식별 가능
