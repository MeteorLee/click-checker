# 13. RDS 전환 종합

## 0. 목적
- 4단계 RDS 전환 작업의 결과를 한 문서에서 요약한다.
- 무엇을 바꿨는지, 무엇이 검증됐는지, 현재 운영 상태가 무엇인지 빠르게 확인할 수 있게 한다.

---

## 1. 전환 목표
- 운영 DB를 EC2 내부 PostgreSQL에서 Amazon RDS for PostgreSQL로 전환한다.
- prod 앱이 RDS를 기준으로 정상 기동하는지 확인한다.
- Flyway, health, smoke, 핵심 데이터 기준값이 전환 전과 일치하는지 검증한다.

---

## 2. 이번 전환에서 한 일

### 2.1 데이터 이전
- EC2 운영 DB를 `pg_dump -Fc`로 백업했다.
- 백업 파일을 기준으로 RDS에 `pg_restore`를 수행했다.
- 복원 후 핵심 row count를 비교했다.

### 2.2 접속/네트워크 확인
- RDS 인스턴스를 생성했다.
- 보안 그룹을 EC2 기준으로 열었다.
- EC2에서 RDS로 `select 1` 접속 확인을 완료했다.

### 2.3 앱 연결 전환
- prod 앱의 DB 설정을 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 기준으로 정리했다.
- `.env`에 RDS endpoint 기준 값을 반영했다.
- 앱 재기동 후 `DB_URL`이 실제로 RDS endpoint를 가리키는지 확인했다.

### 2.4 운영 경로 정리
- prod compose가 로컬 postgres에 의존하지 않도록 구조를 정리했다.
- 배포 workflow도 `DB_*` 필수값을 검사하도록 맞췄다.
- 최종적으로 prod 배포는 로컬 `postgres` 없이도 동작하도록 정리했다.

---

## 3. 검증 결과

### 3.1 스키마 / Flyway
- `flyway_schema_history`: `V1 ~ V9`
- 모든 migration `success=true`

### 3.2 핵심 row count
- `organizations`: `11`
- `users`: `16`
- `events`: `72`

### 3.3 샘플 데이터 집계
- path 집계:
  - `/pricing=12`
  - `/=7`
  - `/blog/rds-migration-checklist=6`
  - `/features/team-analytics=6`
  - `/blog/k6-baseline-guide=2`
- time-bucket 집계:
  - `2026-03-01=11`
  - `2026-03-02=6`
  - `2026-03-03=6`
  - `2026-03-04=6`
  - `2026-03-05=6`

### 3.4 앱 상태
- `/actuator/health = UP`
- prod 앱의 `DB_URL`은 RDS endpoint로 확인됨

---

## 4. 최종 상태
- 운영 앱은 EC2 내부 PostgreSQL이 아니라 RDS를 바라본다.
- RDS restore 후 데이터와 Flyway 기준값이 전환 전과 일치한다.
- prod 배포는 로컬 `postgres` 없이도 정상 동작한다.
- path 집계 / time-bucket 집계 결과도 전환 전과 동일하다.

---

## 5. 남은 정리 항목
- 이번 전환 과정에서 노출된 RDS 비밀번호는 별도 회전 필요
- 기록 문서와 현재 운영 문서의 역할 구분은 유지
- 이후 문서 파생:
  - [11-RDS-전환-런북.md](11-RDS-전환-런북.md): 실행 기록 / 런북
  - [12-RDS-전환-트러블슈팅.md](12-RDS-전환-트러블슈팅.md): 문제와 해결
  - [13-RDS-전환-종합.md](13-RDS-전환-종합.md): 최종 결과 요약

---

## 6. 결론
- 4단계의 핵심 목표였던 RDS 전환은 완료됐다.
- 이번 단계에서 운영 DB 이전, 앱 연결 전환, 데이터/집계 검증까지 끝냈다.
- 이후 작업은 4단계의 연장이 아니라, 다음 단계 운영/배포 고도화로 보는 것이 맞다.
