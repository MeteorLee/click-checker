# 11. RDS 전환 런북

## 0. 목적
- 4단계(RDS 전환)의 리허설/운영 전환 과정을 실제 실행 순서대로 기록한다.
- 전환 전 기준값과 전환 후 비교 결과를 같은 문서에 남긴다.
- 실패 시점과 복구 조치도 추적 가능하게 남긴다.

## 1. 시작 전 고정 정보
- 실행일: `2026-03-07`
- 실행자: `ghtmd`
- 대상 환경: `EC2 prod`
- 앱 버전(commit): `fe924c38cc9d8a6ae37d115ef564b0fcee6d22c2` (`fe924c3`)
- 기준 계획 문서: `docs/00-계획/04-RDS-전환-계획.md`

## 2. 전환 전 기준값 (운영 DB)
### 2.1 스키마 / Flyway
- `flyway_schema_history` 버전: `V1 ~ V9 적용 완료`
- `flyway_schema_history` success 상태: 모든 migration `success=true`
- 애플리케이션 health: `UP` 확인됨

### 2.2 핵심 row count
- `organizations`: `11`
- `users`: `16`
- `events`: `72`

### 2.3 샘플 데이터 검증
- 샘플 조직 1: `id=11`, `Acme Analytics 20260307132558`, `approxEvents=35`
- 샘플 조직 2: `id=12`, `Northstar Commerce 20260307132558`, `approxEvents=29`
- path 집계 확인: `/pricing=12`, `/=7`, `/blog/rds-migration-checklist=6`, `/features/team-analytics=6`, `/blog/k6-baseline-guide=2`
- time-bucket 집계 확인: `2026-03-01=11`, `2026-03-02=6`, `2026-03-03=6`, `2026-03-04=6`, `2026-03-05=6`

### 2.4 백업 정보
- 백업 도구: `pg_dump`
- 백업 형식: `custom dump (-Fc)`
- 표준 백업 명령:
  ```bash
  docker compose exec -T postgres pg_dump -U app -d click_checker -Fc > backup-$(date +%Y%m%d-%H%M%S).dump
  ```
- 메모:
  - 백업 파일명은 실행 시각 포함
  - 백업 후 파일 크기 확인
  - restore 성공 전까지 백업 파일 보관
- 백업 파일명: `backup-20260307-171505.dump`
- 백업 시작 시각: 정확한 시각 미기록
- 백업 종료 시각: 정확한 시각 미기록
- 백업 파일 크기: `31K`

## 3. 리허설 단계
### 3.1 리허설용 RDS 준비
- 예정 설정값:
  - 엔진: `PostgreSQL`
  - 인스턴스 식별자: `click-checker-prod-db`
  - DB 이름: `click_checker`
  - 마스터 사용자명: `app`
  - 인스턴스 클래스: `db.t3.micro`
  - 스토리지: `gp3 20GB`
  - `public access`: `No`
  - VPC: EC2와 동일
  - 보안 그룹: `5432`를 EC2만 허용
- 생성 후 기록값:
  - 인스턴스 식별자: `click-checker-prod-db`
  - 엔드포인트: `click-checker-prod-db.c9uyk8mckabw.ap-northeast-2.rds.amazonaws.com`
- 보안 그룹 확인:
  - RDS `5432` 인바운드는 EC2 보안 그룹 또는 EC2 private IP 대역으로 제한
  - `public access`는 가능하면 비활성
  - EC2 보안 그룹 기준 `5432` 허용 후 `select 1` 접속 확인 완료
- EC2에서 RDS 접속 확인 명령:
  ```bash
  docker compose exec -T postgres psql -h <RDS_ENDPOINT> -U app -d click_checker
  ```

### 3.2 restore 실행
- restore 도구: `pg_restore`
- 표준 restore 명령:
  ```bash
  pg_restore -h <RDS_ENDPOINT> -U <RDS_USER> -d <RDS_DB> --no-owner --no-privileges backup.dump
  ```
- 메모:
  - `pg_dump -Fc`로 생성한 custom dump를 복원할 때 사용
  - RDS와 로컬 PostgreSQL의 owner / privilege 차이로 인한 충돌을 줄이기 위해 `--no-owner --no-privileges` 사용
- restore 시작 시각: 정확한 시각 미기록
- restore 종료 시각: 정확한 시각 미기록
- 결과: 성공 (`organizations=11`, `users=16`, `events=72`, Flyway `V1 ~ V9` 일치 확인)

### 3.3 앱 연결 / Flyway / health
- `DB_URL` 반영: EC2 내부 PostgreSQL 주소 대신 RDS endpoint 기준 JDBC URL로 변경
- `DB_USERNAME` 반영: RDS 접속 계정명으로 변경
- `DB_PASSWORD` 반영: RDS 접속 비밀번호로 변경
- `.env` 반영 예시:
  ```env
  DB_URL=jdbc:postgresql://click-checker-prod-db.c9uyk8mckabw.ap-northeast-2.rds.amazonaws.com:5432/click_checker
  DB_USERNAME=app
  DB_PASSWORD=<RDS_PASSWORD>
  ```
- 메모:
  - `DB_*`는 prod 앱이 실제로 접속할 DB 정보를 의미
  - 기존 `POSTGRES_*`는 로컬 postgres 컨테이너 기동용으로 당장은 유지
  - `deploy-prod.yml`도 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 필수값으로 검사하도록 반영
- Flyway 결과: RDS 기준 `V1 ~ V9`, 모든 migration `success=true`
- health 결과: `/actuator/health = UP`

### 3.4 smoke 검증
- write smoke: 별도 미실행
- read smoke:
  - path 집계 조회 성공
  - time-bucket 집계 조회 성공
- 결과 요약: 샘플 조직(`id=11`) 기준 read smoke 정상, 전환 전 집계 결과와 일치

## 4. 운영 전환 단계
### 4.1 최종 백업
- 시작 시각: 정확한 시각 미기록
- 종료 시각: 정확한 시각 미기록
- 파일명: `backup-20260307-171505.dump`

### 4.2 cutover 실행
- 환경변수 반영:
  - `.env`에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 추가
  - `DB_URL=jdbc:postgresql://click-checker-prod-db.c9uyk8mckabw.ap-northeast-2.rds.amazonaws.com:5432/click_checker`
- 앱 재기동: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build app`
- Flyway 결과: 앱 전환 후에도 RDS 기준 `V1 ~ V9`, 모든 migration `success=true`
- health 결과: `/actuator/health = UP`

### 4.3 전환 후 비교
- `organizations` row count: `11` (전환 전 기준값과 일치)
- `users` row count: `16` (전환 전 기준값과 일치)
- `events` row count: `72` (전환 전 기준값과 일치)
- 샘플 조직 path 집계: `/pricing=12`, `/=7`, `/blog/rds-migration-checklist=6`, `/features/team-analytics=6`, `/blog/k6-baseline-guide=2` (전환 전 기준값과 일치)
- 샘플 조직 time-bucket 집계: `2026-03-01=11`, `2026-03-02=6`, `2026-03-03=6`, `2026-03-04=6`, `2026-03-05=6` (전환 전 기준값과 일치)

## 5. 실패 / 복구 기록
- 실패 시각: 해당 없음
- 실패 지점: 해당 없음
- 원인: 해당 없음
- 조치: 복구 조치 없음
- 최종 상태: RDS 전환 리허설 및 EC2 prod 연결 성공

## 6. 결론
- 리허설 완료 여부: 완료
- 운영 전환 완료 여부: EC2 prod 기준 RDS 연결 완료
- 남은 이슈:
  - 대화/터미널에 노출된 RDS 비밀번호 회전 필요
  - 로컬 `postgres` 컨테이너 유지 여부 추후 결정 필요
- 다음 액션:
  - RDS 비밀번호 변경
  - `deploy-prod.yml` / 운영 문서의 `DB_*` 반영 방식 최종 정리
