# 세션 노트

## 현재 상태
- 목표: 멀티테넌트 B2B 이벤트 분석 백엔드.
- 핵심 범위: `organization > eventUser > path > eventType`.
- `Organization` 생성 API 구현 완료.
- `EventUser` 도메인 뼈대 구현 완료:
  - entity/repository/dto/mapper/service/controller
  - `POST /api/event-users`
- `Event` 생성 흐름:
  - 필수: `organizationId`
  - 선택: `externalUserId`
  - `externalUserId`가 있으면:
    - `(organizationId, externalUserId)`로 조회
    - 없으면 `EventUser` 자동 생성(upsert)
  - `externalUserId`가 null/blank면:
    - 익명 이벤트로 저장(`eventUser = null`)
- Path 집계 API:
  - 필수: `organizationId`
  - 선택: `externalUserId`
  - 선택: `eventType`
  - 시간 조건: `from <= occurredAt < to`
- 시간 버킷 집계 API:
  - 엔드포인트: `GET /api/events/aggregates/time-buckets`
  - 필수: `organizationId`, `from`, `to`, `bucket(HOUR|DAY)`
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

## 다음 권장 작업
1. API Key 1단계 도입:
   - Organization 생성 시 API Key 발급(1회 반환)
   - `POST /api/events`를 `X-API-Key`로 보호(없으면 401)
   - 키로 organization 확정 후 이벤트 저장
   - `organizationId`는 점진 제거(초기엔 일치 검증 병행 가능)
2. API Key 적용 범위 확장:
   - `GET /api/events/aggregates/*`도 `X-API-Key` 기반 조직 확정으로 전환
3. API Key 운영 최소 기능:
   - revoke/rotate(재발급 시 덮어쓰기 방식부터 시작)
   - (선택) `lastUsedAt` 기록
4. README/블로그 문서화 작업은 설날 기간에 별도로 진행(현재 보류).
5. command 흐름에서 사용하지 않는 `EventMapper` 유지/제거 결정.

## 3분 데모 스크립트
1. 조직 생성:
   - `{ "name": "acme" }`로 `POST /api/organizations` 호출
2. 이벤트 적재(같은 조직):
   - 사용자 포함: `externalUserId = "u-1001"`, `eventType = "click"`, `path = /home, /post/1`
   - 익명: `externalUserId` 없이 전송
3. Path 집계 조회:
   - `GET /api/events/aggregates/paths?organizationId={id}&from=...&to=...&top=5`
   - 필요 시 `externalUserId=u-1001` 필터 추가
   - 설명 포인트: 경로별 상위 N개 집계
4. 시간 버킷 집계 조회:
   - `GET /api/events/aggregates/time-buckets?organizationId={id}&from=...&to=...&bucket=HOUR`
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
