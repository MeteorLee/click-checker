# 집계 조회 API 정책 (14단계 1차)

## 목적
- 이벤트 원본을 그대로 저장하면서, 조회 시점에 분석 가능한 기준으로 집계한다.
- `overview`, `routes`, `paths`, `time-buckets` API가 같은 필터 계약을 공유하게 만든다.
- raw path와 routeKey, 식별 사용자 기준을 명시적으로 구분한다.

## 현재 엔드포인트
- `GET /api/events/aggregates/count`
- `GET /api/events/aggregates/overview`
- `GET /api/events/aggregates/paths`
- `GET /api/events/aggregates/routes`
- `GET /api/events/aggregates/time-buckets`

## 공통 인증 / 스코프
- 보호 대상: `/api/events/**`
- 조직 스코프는 `X-API-Key -> authOrgId`로만 결정한다.
- 조회 API는 `organizationId` 쿼리를 받지 않는다.

## 공통 필터 계약
- `from`, `to`
  - 필수
  - 기준: `from <= occurredAt < to`
- `externalUserId`
  - 선택
  - 값이 있으면 해당 외부 사용자 이벤트만 집계한다.
- `eventType`
  - 선택
  - 값이 있으면 해당 이벤트 타입으로 필터링한다.

## path / route 정책

### raw path
- 이벤트 저장 시 들어온 `path` 원본을 그대로 유지한다.
- 예:
  - `/posts/1`
  - `/posts/2`

### routeKey
- 조회 시점에 raw path를 route template 규칙으로 정규화한 값이다.
- 예:
  - `/posts/1`, `/posts/2` -> `/posts/{id}`
- route template이 없거나 매칭되지 않으면 `UNMATCHED_ROUTE`를 사용한다.

### 현재 구현 방향
- routeKey는 아직 이벤트 row에 저장하지 않는다.
- `countRawPathBetween(...)`로 raw path별 count를 먼저 구한다.
- 그 결과를 애플리케이션에서 routeKey 기준으로 다시 합산한다.
- 즉 source of truth는 raw path이고, routeKey는 조회 시 계산값이다.

## overview 정책

### 목적
- 한 기간의 전체 사용량을 한 번에 요약한다.

### 응답 항목
- `totalEvents`
- `uniqueUsers`
- `identifiedEventRate`
- `comparison`
- `topRoutes`
- `topEventTypes`

### 의미
- `totalEvents`
  - 현재 필터 기준 총 이벤트 수
- `uniqueUsers`
  - `organization` 범위 내 `eventUser.id distinct`
  - `eventUser`가 없는 이벤트는 제외
- `identifiedEventRate`
  - `eventUser`가 붙은 이벤트 수 / 전체 이벤트 수
  - `uniqueUsers` 숫자의 해석 보조 지표
- `comparison`
  - 직전 같은 길이 구간과 비교
  - `delta = current - previous`
  - `previous = 0`이면 `deltaRate = null`
- `topRoutes`
  - routeKey 기준 상위 3개
- `topEventTypes`
  - 현재 필터 계약 기준 상위 3개

## paths 정책
- raw path 기준 상세 집계
- 현재는 호환용으로 유지한다.
- 더 이상 확장 중심 API로 보지 않고, route 기반 집계로 점진적으로 옮겨간다.

## routes 정책
- routeKey 기준 상세 집계
- raw path를 직접 노출하는 대신 route template 기준으로 묶은 값을 보여준다.
- `top`은 raw path 단계가 아니라 routeKey 재집계 이후에 적용한다.

## time-buckets 정책
- 시간 구간별 count 집계
- 현재는 전체 이벤트 기준 집계만 제공한다.
- route 기준 trend, comparison 확장은 후속 단계에서 검토한다.

## 식별 사용자 / 익명 이벤트 정책
- 이번 단계에서는 익명 이벤트와 식별 이벤트를 같은 사용자 흐름으로 연결하지 않는다.
- 따라서 `uniqueUsers`, `funnel`, `retention`, `cohort`는 식별된 사용자 기준으로 해석한다.
- 익명 이벤트는 총량/경로/이벤트 타입 집계에는 포함되지만, user 기반 집계에는 포함하지 않는다.
- 후속 단계에서 `anonymousId`, `clientId`, `sessionId` 같은 연결 키 도입을 검토한다.

## 현재 제한
- timezone 반영은 아직 본격적으로 붙지 않았다.
- `topEventTypes`의 필터 의미는 현재 공통 필터 계약을 그대로 따른다.
- routeKey × eventType 교차 집계는 아직 없다.
- route 기준 time-bucket trend는 아직 없다.

## 현재 테스트 커버리지
- `EventCommandControllerIntegrationTest`
- `EventQueryControllerIntegrationTest`
- `EventQueryServiceTest`
- `RoutePathMatcherTest`
- `RouteKeyResolverTest`

## 회귀 검증 명령
- `./gradlew test --tests com.clickchecker.event.controller.EventQueryControllerIntegrationTest --tests com.clickchecker.event.controller.EventCommandControllerIntegrationTest --tests com.clickchecker.event.service.EventQueryServiceTest --tests com.clickchecker.route.service.RoutePathMatcherTest --tests com.clickchecker.route.service.RouteKeyResolverTest`
