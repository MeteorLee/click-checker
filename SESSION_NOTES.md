# 세션 노트

## 현재 상태
- 목표: 멀티테넌트 B2B 이벤트 분석 백엔드.
- 핵심 범위: `organization > eventUser > path > eventType`.
- 운영 prod 앱은 현재 Amazon RDS for PostgreSQL을 바라본다.
- 운영 배포 구조는 현재 `GitHub Actions -> ECR -> S3 -> CodeDeploy -> EC2 -> Blue/Green` 기준으로 정리돼 있다.
- local postgres는 local 개발/검증용으로만 유지한다.
- `Organization` 생성 시 API Key 발급/해시 저장 구현 완료.
- `EventUser` 도메인 뼈대 구현 완료:
  - entity/repository/dto/mapper/service/controller
  - `POST /api/event-users`
- `Event` 생성 흐름:
- 필수: `X-API-Key` (헤더 인증)
- 선택: `externalUserId`
  - `externalUserId`가 있으면:
    - `(authOrgId, externalUserId)`로 조회
    - 없으면 `EventUser` 자동 생성(upsert)
  - `externalUserId`가 null/blank면:
    - 익명 이벤트로 저장(`eventUser = null`)
- Path 집계 API:
  - 필수: `X-API-Key` (헤더 인증)
  - 선택: `externalUserId`
  - 선택: `eventType`
  - 시간 조건: `from <= occurredAt < to`
- 시간 버킷 집계 API:
  - 엔드포인트: `GET /api/events/aggregates/time-buckets`
  - 필수: `X-API-Key`, `from`, `to`, `bucket(HOUR|DAY)`
  - 선택: `externalUserId`, `eventType`
  - 시간 조건: `from <= occurredAt < to`
- 시간 버킷 입력 처리 참고:
  - 현재 `bucket`은 enum(`HOUR`, `DAY`) 직접 바인딩 기준으로 동작
  - 소문자 입력(`hour/day`) 유연 처리와 에러 메시지 개선은
    이후 요청 DTO 리팩터링 시점에 함께 정리하기로 결정
- 운영 관측 기준:
  - Grafana / Prometheus / `k6` 기반으로 배포 실전 검증 가능한 상태다.
  - 배포 실전 검증 기준으로는 `read`는 바로 통과했고, `write`는 1차 실패 후 old color drain 대기 추가 뒤 재검증에 통과했다.
- 종료 절차 개선 기준:
  - `deploy-prod-orchestrator -> deploy-smoke -> deploy-drain` 구조로 정리됐다.
  - 앱 내부에는 `TrafficState`, `ActiveRequestTracker`, `/internal/drain/*`가 추가됐다.
  - 운영 nginx는 `/internal/` 외부 접근을 차단한다.
  - 배포 중 infra 단계와 `prometheus -> app-blue` 의존성 때문에 active app 조기 recreate가 발생할 수 있음을 확인했고, 현재는 그 수정까지 반영된 상태다.

## 구현된 파일 (상위)
- EventUser 도메인:
  - `src/main/java/com/clickchecker/eventuser/entity/EventUser.java`
  - `src/main/java/com/clickchecker/eventuser/repository/EventUserRepository.java`
  - `src/main/java/com/clickchecker/eventuser/dto/EventUserCreateRequest.java`
  - `src/main/java/com/clickchecker/eventuser/service/EventUserService.java`
  - `src/main/java/com/clickchecker/eventuser/controller/EventUserController.java`
  - `src/main/java/com/clickchecker/mapper/EventUserMapper.java`
- Event/EventQuery 업데이트:
  - `src/main/java/com/clickchecker/event/dto/EventCreateRequest.java`
  - `src/main/java/com/clickchecker/event/entity/Event.java`
  - `src/main/java/com/clickchecker/event/service/EventCommandService.java`
  - `src/main/java/com/clickchecker/event/controller/EventQueryController.java`
  - `src/main/java/com/clickchecker/event/service/EventQueryService.java`
  - `src/main/java/com/clickchecker/event/repository/EventQueryRepository.java`
  - `src/main/java/com/clickchecker/event/repository/dto/TimeBucket.java`
  - `src/main/java/com/clickchecker/event/repository/dto/TimeBucketCountDto.java`
- HTTP 샘플:
  - `api-scenarios/event.http`
  - `api-scenarios/event-user.http`
  - `api-scenarios/organization.http`
- 배포 / 운영 스크립트:
  - `scripts/deploy-prod-orchestrator.sh`
  - `scripts/blue-green-prod-lib.sh`
  - `scripts/codedeploy/after-install.sh`
  - `scripts/codedeploy/application-start.sh`
  - `scripts/codedeploy/validate-service.sh`
- 부하 검증:
  - `k6/smoke-read.js`
  - `k6/smoke-write.js`
- 운영 관측:
  - `prometheus/prometheus.yml`
- 줄바꿈 정책:
  - `.gitattributes`에서 LF 정규화 적용

## 테스트 상태
- 추가/수정된 테스트:
  - `src/test/java/com/clickchecker/event/controller/EventCommandControllerIntegrationTest.java`
  - `src/test/java/com/clickchecker/event/controller/EventQueryControllerIntegrationTest.java`
  - `src/test/java/com/clickchecker/event/controller/EventQueryControllerPostgresIntegrationTest.java`
  - `src/test/java/com/clickchecker/eventuser/controller/EventUserControllerIntegrationTest.java`
  - `src/test/java/com/clickchecker/organization/controller/OrganizationControllerIntegrationTest.java`
- FK 안전 정리 순서 표준화:
  - `event -> eventUser -> organization`
- 최근 타깃 통합테스트 통과:
  - `EventCommandControllerIntegrationTest`
  - `EventQueryControllerIntegrationTest`
  - `EventUserControllerIntegrationTest`

## 참고 사항
- `/api/events/aggregates/count`는 개발/디버그 용도로 유지
- `ApiKeyAuthFilter` 보호 범위는 현재 `/api/events/**`만 적용
- `EventUser` API(`/api/event-users`)는 아직 `organizationId` 요청 방식 유지(후속 정리 대상)
- prod 배포는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 기준으로 동작
- `POSTGRES_*`는 local postgres 컨테이너 기동용으로만 본다

## 다음 권장 작업
1. 샘플 시나리오 / 문서 계약 정합화
   - `k6`, `api-scenarios`, 오래된 문서 예시를 현재 `X-API-Key` 계약 기준으로 마감 정리한다.
2. 운영 관측 기준 최소 정리
   - 현재 Grafana / Prometheus / `k6` 기준을 대시보드/알림 기준으로 짧게 고정한다.
3. `EventUser` API 테넌트 스코프 정합성 정리
   - `/api/event-users`도 인증 org 기반 전환 여부를 결정한다.
4. 종료 절차 고도화 후속 검토
   - 현재 direct smoke + internal drain 구조는 안정화됐고, 이후에는 active request 기반 drain 판단 고도화를 검토한다.

## 최근 업데이트 (추가)
- RDS 전환 완료:
  - EC2 운영 DB -> RDS `pg_dump` / `pg_restore` 완료
  - row count(`organizations=11`, `users=16`, `events=72`) 일치 확인
  - Flyway `V1~V9` 및 `success=true` 일치 확인
  - health / path 집계 / time-bucket 집계가 전환 전 기준과 동일함을 확인
  - prod 앱 `DB_URL`이 RDS endpoint를 바라보는 것 확인
- prod 배포 구조 정리:
  - `docker-compose.yml` / `docker-compose.local.yml` / `docker-compose.prod.yml` 역할 재분리
  - local만 postgres 의존, prod는 RDS-only 구조로 정리
  - `deploy-prod.yml`에서 `postgres` 동시 기동 제거
  - `deploy-prod.yml`의 필수값 검사를 `DB_*`, `API_KEY_*`, `SENTRY_DSN` 중심으로 정리
- 운영 문서 정리:
  - `docs/05-배포/11-RDS-전환-런북.md`, `12-RDS-전환-트러블슈팅.md`, `13-RDS-전환-종합.md` 추가
  - `docs/05-배포/01-배포-개요.md`, `02-EC2-프로젝트-설정.md` 현재 구조 기준으로 정리
  - `docs/04-운영-설계/배포-운영-체크리스트.md`, `K6-스모크-런북.md`, `ci-품질선.md` 갱신
- API Key 인증 구조 마무리 후 CI/CD 정리 진행:
  - `deploy-prod.yml` 스모크 테스트를 API Key 인증 경로에 맞게 수정
  - Organization 생성 응답에서 `apiKey` 추출 후 `X-API-Key` 헤더로 이벤트 저장/조회 호출
  - 스모크 시간 파라미터를 `Instant` 기준으로 `Z`(UTC) 포맷으로 통일
- 운영 배포 이슈 확인:
  - 배포 후 health는 통과했지만 `POST /api/organizations`에서 `500` 발생
  - Sentry/컨테이너 로그로 원인 확정:
    - `users`, `organizations`의 auditing 컬럼(`created_at`, `updated_at`) 스키마 불일치
    - `ddl-auto=update`가 기존 데이터 + NOT NULL 제약 상황에서 완전 반영 실패
- 환경변수 정리 이슈:
  - 앱 설정 키는 `API_KEY_PEPPER` 기준
  - compose에는 `APP_API_KEY_PEPPER` 전달이 남아 있어 이후 키 이름 통일 필요
- Flyway 전환 계획 문서 확정:
  - `docs/00-계획/03-Flyway-전환-계획.md` v1.1 작성/수정 완료
  - 핵심 원칙: Flyway 체계 전환, V1 기준선 우선, 위험 변경 분할(추가 -> 백필 -> 제약)
  - baseline 전략 환경 분기(기존 DB baseline, 신규 DB V1부터 migrate) 명시
- Flyway 구현/검증 완료:
  - 마이그레이션 추가:
    - `V1__baseline.sql`
    - `V2~V4`: auditing 컬럼 추가/백필/NOT NULL
    - `V5~V7`: organizations 제약 강화(name/status/prefix/kid/hash)
    - `V8`: `events.path` NOT NULL
    - `V9`: `events.occurred_at` -> `TIMESTAMPTZ`
  - 코드 정합성 반영:
    - `Event.path`를 엔티티에서도 `nullable = false`로 정렬
    - `application-local.yml`, `application-prod.yml`의 `ddl-auto`를 `validate`로 전환
  - CI 안정화 반영:
    - `application-ci.yml`에서 `spring.flyway.enabled=false`로 H2 fast lane 유지
    - `build.gradle`에 `flyway-database-postgresql` 추가(PostgreSQL 16 지원)
  - 운영 반영 결과:
    - EC2 DB 백업 수행 완료
    - Flyway baseline + V2~V9 적용 완료
    - `flyway_schema_history` 기준 버전 1~9 성공 확인
    - `/actuator/health` `UP` 확인
  - 배포 파이프라인 안정화(추가):
    - `deploy-prod.yml`에서 `compose_up` 실패 시에도 `rollback()` 진입하도록 보강
    - 배포 시작 전 `.env` 필수 키 사전검증 추가:
      - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `API_KEY_PEPPER`, `API_KEY_ENV`
    - 목적: `set -e` 조기 종료로 롤백 미실행되던 케이스 방지
- Flyway 최종 마무리(추가):
    - `POST /api/organizations` 500(`api_key_hash` NOT NULL 위반) 원인 수정
    - 조직 생성 시 API Key를 선발급해 `apiKeyKid/hash/prefix`를 insert 시점에 저장하도록 정렬
    - `prod` 배포 스모크 통과로 Flyway 전환 종료 확인
- Blue/Green / CodeDeploy 운영 구조 정리:
  - Blue/Green 전환 구조를 운영 기준으로 적용
  - `GitHub Actions -> ECR -> S3 -> CodeDeploy -> EC2 -> Blue/Green` 흐름 정리 완료
  - EC2 `/home/ubuntu/click-checker`는 `.git` 없는 배포 디렉터리 기준으로 재정리 완료
- 운영 관측 경로 정리:
  - Prometheus scrape target을 `app:8080`에서 `app-blue:8081`, `app-green:8082`로 수정
  - Grafana에서 운영 Prometheus 데이터 소스를 연결하고 실전 검증 쿼리(`RPS`, `p95`, `p99`, `5xx`)를 확인
- 배포 실전 검증 완료:
  - `read` baseline / 배포 중 검증 완료
  - `write` baseline / 배포 중 검증 완료
  - 1차 `write` 검증 실패(`http_req_failed = 5.57%`)를 재현하고 Nginx upstream reset 정황 확인
  - `OLD_COLOR_DRAIN_SECONDS` 추가 후 같은 조건 재검증에서 `http_req_failed = 0.00%` 확인
  - 관련 문서 정리:
    - `docs/05-배포/25-배포-실전-검증-런북.md`
    - `docs/05-배포/26-배포-실전-검증-기록.md`
    - `docs/05-배포/27-배포-실전-검증-트러블슈팅.md`
    - `docs/05-배포/28-배포-실전-검증-종합.md`
- 종료 절차 개선 완료:
  - 앱 내부 draining 지원 추가
    - `TrafficState`, `TrafficStateManager`
    - `ActiveRequestTracker`, `ActiveRequestTrackingFilter`
    - `POST /internal/drain/start`, `GET /internal/drain/status`
  - 배포 스크립트 구조 정리
    - `scripts/deploy-prod-orchestrator.sh`
    - `scripts/deploy-smoke.sh`
    - `scripts/deploy-drain.sh`
    - `scripts/blue-green-prod-lib.sh`
  - 운영 nginx `/internal/` 외부 차단 적용
  - 개선 중 실제 이슈 확인 및 수정
    - 새 smoke/drain 스크립트 실행 권한 누락
    - `internal drain` content negotiation 문제
    - 배포 중 infra 단계와 `prometheus -> app-blue` 의존성으로 active app 조기 recreate 발생
  - 최종 수정
    - `docker-compose.prod.yml`에서 `prometheus` override 제거
    - 배포 중 infra 단계 제거
  - 최종 write 재검증 성공
    - `T0/T1 = 10:06`
    - `T2 = 10:09:19`
    - `T3 = 10:11 전후`
    - `http_req_failed = 0.00%`, `p95 = 44.26ms`, `p99 = 112.69ms`
  - 관련 문서 정리:
    - `docs/05-배포/29-종료-절차-개선-런북.md`
    - `docs/05-배포/30-종료-절차-개선-기록.md`
    - `docs/05-배포/31-종료-절차-개선-트러블슈팅.md`
    - `docs/05-배포/32-종료-절차-개선-종합.md`
    - `docs/00-실행기록/12-종료-절차-개선-실행기록.md`

## 3분 데모 스크립트
1. 조직 생성:
   - `{ "name": "acme" }`로 `POST /api/organizations` 호출
   - 응답 `apiKey`를 안전하게 보관
2. 이벤트 적재(같은 조직):
   - 헤더 `X-API-Key: {apiKey}`
   - 사용자 포함: `externalUserId = "u-1001"`, `eventType = "click"`, `path = /home, /post/1`
   - 익명: `externalUserId` 없이 전송
3. Path 집계 조회:
   - 헤더 `X-API-Key: {apiKey}`
   - `GET /api/events/aggregates/paths?from=...&to=...&top=5`
   - 필요 시 `externalUserId=u-1001` 필터 추가
   - 설명 포인트: 경로별 상위 N개 집계
4. 시간 버킷 집계 조회:
   - 헤더 `X-API-Key: {apiKey}`
   - `GET /api/events/aggregates/time-buckets?from=...&to=...&bucket=HOUR`
   - 필요 시 `eventType=click` 또는 `externalUserId=u-1001` 필터 추가
   - 설명 포인트: 시간 추이(`bucketStart`, `count`)
5. 멀티테넌트 격리 확인:
   - 다른 조직(`globex`) 생성
   - 동일 `externalUserId = "u-1001"`로 이벤트 적재
   - 조직별 조회 결과가 분리되는지 확인

## 문서/포트폴리오 메모
- `README_DRAFT.md`를 임시로 생성해 채용용 구조 초안을 준비함.
- 최종 README 개편은 지금 하지 않고, 설날 기간에 한 번에 정리하기로 함.
- 데모 실행 문서는 `DEMO_RUNBOOK.md` 기준으로 유지.

## 이번 세션 합의 작업 규칙
- 파일은 기본적으로 1개씩 작업한다.
- 다만 한 주제 정리에 꼭 필요할 때만 여러 파일을 함께 수정한다.
- 작업 전 2~3문장으로 변경 내용과 이유를 설명.
- 아래 작업은 항상 명시 승인 후 실행:
  - 파일 수정
  - 빌드/테스트 명령
  - DB/Docker 관련 명령
- 범위 실수 발생 시 즉시 중단하고 되돌리기.
- 장문 계획보다 구현 중심으로 빠르게 진행.
- 응답은 간결하게, "왜?" 질문에는 기술 근거 포함.
- 도메인 규칙은 서비스 계층 검증, 리포지토리는 조회 책임 중심 유지.
- 불확실하면 먼저 코드 읽고 가장 작은 안전 변경부터 제안.
- 문서/README 업데이트는 요청 시점까지 지연.
