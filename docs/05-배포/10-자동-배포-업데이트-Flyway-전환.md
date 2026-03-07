# 자동 배포 업데이트 09 - Flyway 전환 반영

## 1. 목적
- 3단계(Flyway 전환) 작업 결과를 배포 관점에서 최신 상태로 기록한다.
- 운영 배포 시 DB 스키마 변경이 코드와 함께 안전하게 반영되도록 절차를 고정한다.

---

## 2. 이번 업데이트 범위
- Flyway 기반 스키마 관리 전환
- `ddl-auto` 의존 축소 (`validate` 전환)
- 배포 스모크(API Key 인증 포함) 보강
- 운영 DB baseline + migration 실제 반영

---

## 3. 적용된 변경

### 3.1 Flyway 마이그레이션
- `V1__baseline.sql`: 기존 운영 스키마 기준선 고정
- `V2~V4`: auditing 컬럼 추가/백필/NOT NULL
- `V5~V7`: `organizations` 제약 강화 (`name/status/prefix/kid/hash`)
- `V8`: `events.path` NOT NULL
- `V9`: `events.occurred_at` -> `TIMESTAMPTZ`

### 3.2 애플리케이션 설정
- `application-local.yml`, `application-prod.yml`
  - `spring.jpa.hibernate.ddl-auto=validate`
- `application.yml`
  - Flyway 공통 활성화(`spring.flyway.enabled=true`)

### 3.3 CI/CD 보강
- `application-ci.yml`
  - H2 fast test 경로에서 Flyway 비활성화(`spring.flyway.enabled=false`)
- `build.gradle`
  - `flyway-database-postgresql` 추가(PostgreSQL 16 대응)
- `deploy-prod.yml`
  - 스모크 테스트에 `X-API-Key` 헤더 반영
  - 시간 파라미터 UTC `Z` 포맷 반영
  - 트리거: `prod` push + `workflow_dispatch`

---

## 4. 운영 반영 결과 (실행 기록)
- EC2 DB 백업 완료
- Flyway baseline 적용 완료
- `V2~V9` migration 적용 완료
- `flyway_schema_history` 확인:
  - version `1`(baseline) ~ `9` 성공(`success = true`)
- `/actuator/health` 확인: `UP`

---

## 5. 운영 원칙 (현재 기준)
- 운영은 Flyway를 상시 사용한다.
- `baseline-on-migrate`는 최초 전환 시 1회성으로만 사용한다.
- 이후 스키마 변경은 `V{N}__*.sql` 추가 후 배포로 반영한다.
- 운영 반영 전 DB 백업을 기본 절차로 유지한다.
