# 03. Flyway 전환 계획 (v1.1)

## 3단계 목표
- 현재 스키마를 Flyway 기준으로 동결한다.
- `ddl-auto` 의존을 제거하고 이후 DB 변경은 migration 파일로만 관리한다.
- 배포 루틴에 migrate를 포함해 운영 반영 절차를 표준화한다.

---

# 3.0 고정 원칙

## 1. Hibernate 자동 스키마 변경에 의존하지 않는다

목표

```
spring.jpa.hibernate.ddl-auto=validate
```

역할 분리

```
Flyway      → schema create / migrate
Hibernate   → entity ↔ schema mismatch 검증
```

---

## 2. 기준선 먼저, 변경은 나중

현재 실제 스키마를 `V1` 기준선으로 먼저 확정한다.

```
V1 (baseline)
↓
V2, V3, V4 ... (migration)
```

`V1`보다 후속 변경이 먼저 오면 안 된다.

---

## 3. 위험 변경은 3단계 분할 적용

운영 DB 변경은 다음 순서를 따른다.

```
컬럼 추가 (nullable)
↓
데이터 백필 (backfill)
↓
NOT NULL / 제약 강화
```

---

## 4. 운영 배포는 DB 변경을 포함한다

배포 순서를 고정한다.

```
DB 백업
↓
DB migrate
↓
애플리케이션 배포
↓
Health Check
↓
Smoke Test
```

## 5. 환경변수 키 이름을 통일한다

API Key pepper 주입 키를 단일 이름으로 고정한다.

고정 키

```
API_KEY_PEPPER
```

원칙

- `application-*.yml`, `docker-compose*.yml`, EC2 `.env`에서 동일 키를 사용한다.
- 임시/중복 키(`APP_API_KEY_PEPPER`)는 3단계에서 정리한다.

---

# 3.1 Flyway 도입 및 실행 경로 통일

## 작업

- Flyway 의존성 추가
- `spring.flyway.*` 기본 설정 추가
- 로컬 / CI / 운영 환경에서 migrate 실행 경로 통일
- Hibernate 자동 스키마 변경 축소

```
spring.jpa.hibernate.ddl-auto=validate
```

---

## 완료 기준

- 애플리케이션 실행 시 Flyway가 migration을 관리한다.
- 새 DB에서 migrate만으로 스키마가 생성된다.
- `ddl-auto=validate` 상태에서 애플리케이션이 정상 기동한다.

---

# 3.2 기준 스키마 V1 확정 (핵심)

## 작업

- 현재 스키마 기준 `V1__baseline.sql` 작성
- 신규 DB에서 `V1`만으로 동일 스키마 재현 가능하도록 검증
- 기존 운영 DB baseline 전략 확정

가능한 방식

```
baselineOnMigrate
또는
수동 baseline 기록
```

환경별 baseline 분기(고정)

```
A. 기존 운영/배포 DB(데이터 있음)
   - baseline만 기록하고, 이후 V2+ 적용

B. 신규 DB(로컬/테스트/신규 운영)
   - V1부터 migrate 순차 적용
```

---

## 검증 절차

```
새 DB 생성
↓
Flyway migrate 실행
↓
애플리케이션 실행 (ddl-auto=validate)
↓
엔티티 ↔ 스키마 일치 검증
```

---

## 완료 기준

- 현재 구조가 공식 스키마 기준선이 된다.
- 신규 DB는 `V1`만으로 동일 스키마 재현 가능하다.
- baseline 전략이 문서화된다.

---

# 3.3 후속 마이그레이션 1차 (운영 이슈 우선)

## 우선순위 A: 감사 컬럼 (현재 장애 원인)

대상 테이블

```
users
organizations
```

추가 컬럼

```
created_at
updated_at
```

---

### 적용 단계

1️⃣ nullable 컬럼 추가

```
V2__add_audit_columns_nullable.sql
```

2️⃣ 기존 데이터 백필

```
V3__backfill_audit_columns.sql
```

3️⃣ NOT NULL 적용

```
V4__audit_columns_not_null.sql
```

---

## 우선순위 B: API Key 제약 강화

대상

```
organizations
```

작업

- 기존 데이터 백필
- NOT NULL 적용
- unique 제약 검토
- status 컬럼 검증
- 필요 시 prefix 인덱스 검토

예시

```
index on api_key_prefix
```

---

## 완료 기준

- 운영 DB에서 컬럼 누락 / NOT NULL 오류 없이 정상 기동
- 쓰기 API 정상 동작

---

# 3.4 후속 마이그레이션 2차

## 작업

- 1차에서 다루지 않은 나머지 구조 반영
- 필요한 인덱스 / 제약 최소 보강

예시

```
index
unique
foreign key
```

---

## 완료 기준

- 모든 스키마 변경 이력이 migration 파일로 관리된다.

---

# 3.5 테스트 / CI 전환

## 작업

Flyway 기반 검증 순서를 확립한다.

1️⃣ Postgres 기반 테스트

```
postgresTest
```

2️⃣ 일반 테스트 경로 정리

```
test
```

3️⃣ CI 전체 검증

```
ddl-auto 없이 CI 통과
```

---

## 회귀 검증 기준

다음 핵심 기능이 정상 동작해야 한다.

1. organization 생성
2. API Key 인증
3. event 저장
4. aggregate 조회

---

# 3.6 운영 반영 절차 표준화

운영 배포 시 다음 절차를 따른다.

```
DB 백업
↓
Flyway migrate
↓
애플리케이션 배포
↓
Health Check
↓
Smoke Test
```

---

## Health Check

애플리케이션 기동 여부 확인

예시

```
/actuator/health
```

---

## Smoke Test

핵심 기능 정상 동작 검증

예시

```
organization 생성
API Key 인증
event 저장
aggregate 조회
```

---

## 완료 기준

- 운영 배포 runbook에 DB migrate 단계가 포함된다.
- 실패 시 롤백 절차가 문서화된다.

---

# 3단계 권장 실행 순서

1. Flyway 도입 및 실행 경로 통일
2. 현재 스키마 기준 V1 확정
3. 후속 마이그레이션 1차
  - 현재 상황에서는 감사 컬럼 먼저 처리
4. 후속 마이그레이션 2차
5. CI 전환 완료
6. 운영 반영 절차 문서화
