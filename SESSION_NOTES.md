# 세션 노트

## 현재 상태
- 목표: 멀티테넌트 B2B 이벤트 분석 백엔드.
- 핵심 범위: `organization > eventUser > path > eventType`.
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
  - `http/event.http`
  - `http/event-user.http`
  - `http/organization.http`
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
- `EventMapper` 경고 유지:
  - unmapped target properties: `organization`, `eventUser`
  - 현재는 서비스 계층에서 관계를 주입하므로 허용
- `/api/events/aggregates/count`는 개발/디버그 용도로 유지
- `ApiKeyAuthFilter` 보호 범위는 현재 `/api/events/**`만 적용
- `EventUser` API(`/api/event-users`)는 아직 `organizationId` 요청 방식 유지(후속 정리 대상)

## 다음 권장 작업
1. 3단계 후속 문서/운영 정리:
   - Flyway 운영 반영 절차(runbook) 고정
   - baseline 1회 적용 원칙(운영에서는 상시 비활성) 명시
2. API Key 로그 하드닝:
   - 인증 로그 정책 정리(원문 키 미노출)
3. `EventUser` API 테넌트 스코프 정합성 정리:
   - `/api/event-users`도 인증 org 기반 전환 여부 결정
4. `/api/events/aggregates/count` 운영 노출 여부 확정(유지/차단)

## 최근 업데이트 (추가)
- 2단계 마무리 후 CI/CD 정리 진행:
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
  - compose에는 `APP_API_KEY_PEPPER` 전달이 남아 있어 3단계에서 키 이름 통일 필요
- 3단계 계획 문서 확정:
  - `docs/00-계획/03-Flyway-전환-계획.md` v1.1 작성/수정 완료
  - 핵심 원칙: Flyway 체계 전환, V1 기준선 우선, 위험 변경 분할(추가 -> 백필 -> 제약)
  - baseline 전략 환경 분기(기존 DB baseline, 신규 DB V1부터 migrate) 명시
- 3단계(Flyway) 구현/검증 완료:
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
- 파일은 1개씩만 작업(배치 수정 금지).
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
