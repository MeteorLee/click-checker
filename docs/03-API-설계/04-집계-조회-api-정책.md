# 집계 조회 API 정책 (14단계 1차)

## 목적
- 이벤트 원본을 그대로 저장하면서, 조회 시점에 분석 가능한 기준으로 집계한다.
- `overview`, `routes`, `paths`, `time-buckets` API가 같은 필터 계약을 공유하게 만든다.
- raw path와 routeKey, 식별 사용자 기준을 명시적으로 구분한다.

## 현재 엔드포인트
- `GET /api/events/aggregates/count`
- `GET /api/events/aggregates/overview`
- `GET /api/events/aggregates/raw-event-types`
- `GET /api/events/aggregates/event-types`
- `GET /api/events/aggregates/event-types/unique-users`
- `GET /api/events/aggregates/route-event-types`
- `GET /api/events/aggregates/route-event-type-time-buckets`
- `GET /api/events/aggregates/route-time-buckets`
- `GET /api/events/aggregates/event-type-time-buckets`
- `GET /api/events/aggregates/paths`
- `GET /api/events/aggregates/routes`
- `GET /api/events/aggregates/routes/unique-users`
- `GET /api/events/aggregates/time-buckets`
- `GET /api/events/route-templates`
- `POST /api/events/route-templates`
- `PUT /api/events/route-templates/{id}`
- `PUT /api/events/route-templates/{id}/active`
- `DELETE /api/events/route-templates/{id}`
- `GET /api/events/event-type-mappings`
- `POST /api/events/event-type-mappings`
- `PUT /api/events/event-type-mappings/{id}`
- `PUT /api/events/event-type-mappings/{id}/active`
- `DELETE /api/events/event-type-mappings/{id}`

## 공통 인증 / 스코프
- 보호 대상: `/api/events/**`
- 조직 스코프는 `X-API-Key -> authOrgId`로만 결정한다.
- 조회 API는 `organizationId` 쿼리를 받지 않는다.

## 공통 필터 계약
- `from`, `to`
  - 필수
  - 기준: `from <= occurredAt < to`
  - `Instant` 절대시간 범위로 해석한다.
- `externalUserId`
  - 선택
  - 값이 있으면 해당 외부 사용자 이벤트만 집계한다.
- `eventType`
  - 선택
  - 값이 있으면 해당 이벤트 타입으로 필터링한다.
- `timezone`
  - 시계열 API에서만 사용한다.
  - `from`, `to` 자체를 다시 해석하는 용도가 아니라, bucket 경계를 어떤 시간대로 자를지 정하는 용도다.

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

### route template 관리 정책
- route template는 조직 스코프에서 관리한다.
- 현재는 `생성 / 목록 / 수정 / 활성-비활성 / 삭제`를 지원한다.
- 일상적인 중지는 `active=false`를 우선 사용하고, 삭제는 잘못 만든 규칙 정리용으로 본다.

## overview 정책

### 목적
- 한 기간의 전체 사용량을 한 번에 요약한다.

### 응답 항목
- `totalEvents`
- `uniqueUsers`
- `identifiedEventRate`
- `eventTypeMappingCoverage`
- `routeMatchCoverage`
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
- `eventTypeMappingCoverage`
  - raw eventType이 있는 이벤트 중 canonical eventType으로 정상 매핑된 이벤트 비율
  - `topEventTypes`와 eventType 기반 집계 숫자의 해석 보조 지표
  - 현재는 `eventType` 필터가 없는 overview에서만 계산하고, raw eventType으로 이미 좁힌 overview에서는 `null`로 둔다
- `routeMatchCoverage`
  - raw path가 있는 이벤트 중 route template에 정상 매칭된 이벤트 비율
  - `topRoutes`와 route 기반 집계 숫자의 해석 보조 지표
  - 현재는 `eventType` 필터가 있어도 그대로 계산한다
- `comparison`
  - 직전 같은 길이 구간과 비교
  - `delta = current - previous`
  - `previous = 0`이면 `deltaRate = null`
- `topRoutes`
  - routeKey 기준 상위 3개
- `topEventTypes`
  - `eventType` 필터가 없으면 canonical eventType 기준 상위 3개
  - `eventType` 필터가 있으면 현재 raw eventType 필터 집합 안에서 상위 3개

## eventType 정책

### raw eventType
- 이벤트 저장 시 들어온 `eventType` 원본을 그대로 유지한다.
- 예:
  - `button_click`
  - `page_view`

### canonical eventType
- 조회 시점에 raw eventType을 조직별 매핑 규칙으로 정규화한 값이다.
- 예:
  - `button_click`, `post_click` -> `click`
  - `page_view` -> `view`
- 매핑이 없거나 raw 값이 비어 있으면 `UNMAPPED_EVENT_TYPE`을 사용한다.

### 현재 구현 방향
- canonical eventType도 아직 이벤트 row에 저장하지 않는다.
- `countRawEventTypeBetween(...)`로 raw eventType별 count를 먼저 구한다.
- 그 결과를 애플리케이션에서 canonical eventType 기준으로 다시 합산한다.
- 즉 source of truth는 raw eventType이고, canonical eventType은 조회 시 계산값이다.

### eventType mapping 관리 정책
- eventType mapping도 조직 스코프에서 관리한다.
- 현재는 `생성 / 목록 / 수정 / 활성-비활성 / 삭제`를 지원한다.
- 일상적인 중지는 `active=false`를 우선 사용하고, 삭제는 잘못 만든 규칙 정리용으로 본다.

## raw-event-types 정책
- raw eventType 기준 상세 집계
- 현재 들어오고 있는 원본 값의 분포를 운영/정리 관점에서 확인하는 용도다.
- 매핑 전 관찰용 API로 사용한다.

## event-types 정책
- canonical eventType 기준 상세 집계
- raw eventType을 직접 노출하는 대신 조직별 매핑 규칙으로 묶은 값을 보여준다.
- `top`은 raw eventType 단계가 아니라 canonical 재집계 이후에 적용한다.

## event-types/unique-users 정책
- canonical eventType 기준 unique user 상세 집계
- raw eventType을 직접 노출하지 않고, 조직별 매핑 규칙으로 묶은 canonical eventType별 distinct `eventUser.id` 수를 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `eventType + eventUserId` raw 조합을 먼저 구한다.
  2. `rawEventType -> canonicalEventType`를 적용한다.
  3. 같은 canonical eventType 안에서 distinct `eventUser.id` 수를 계산한다.
  4. `top`은 최종 canonical eventType unique user 결과에 적용한다.
- 매핑되지 않은 eventType은 `UNMAPPED_EVENT_TYPE`으로 포함할 수 있다.

## route-event-types 정책
- routeKey × canonical eventType 교차 집계
- raw path와 raw eventType을 직접 노출하지 않고, 두 축을 각각 정규화한 뒤 조합별 count를 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `path + eventType` raw 조합별 count를 먼저 구한다.
  2. `path -> routeKey`, `rawEventType -> canonicalEventType`를 각각 적용한다.
  3. 같은 `(routeKey, canonicalEventType)` 조합끼리 다시 합산한다.
  4. `top`은 최종 교차 집계 결과에 적용한다.
- 매핑되지 않은 eventType은 `UNMAPPED_EVENT_TYPE`으로 포함한다.

## route-event-type-time-buckets 정책
- routeKey × canonical eventType × time bucket 교차 시계열 집계
- raw path와 raw eventType을 직접 노출하지 않고, 두 축을 각각 정규화한 뒤 시간 흐름까지 함께 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `path + eventType + occurredAt` raw 조합별 count를 먼저 구한다.
  2. `path -> routeKey`, `rawEventType -> canonicalEventType`를 각각 적용한다.
  3. `occurredAt -> timezone 기준 bucketStart`를 적용한다.
  4. 같은 `(routeKey, canonicalEventType, bucketStart)` 조합끼리 다시 합산한다.
  5. 실제로 등장한 `(routeKey, canonicalEventType)` 축 조합에 대해 요청 구간 안의 빈 bucket은 `0`으로 채운다.
- 매핑되지 않은 eventType은 `UNMAPPED_EVENT_TYPE`으로 포함한다.

## route-time-buckets 정책
- routeKey 기준 time-bucket trend 집계
- raw path를 직접 노출하지 않고, route template 기준으로 정규화한 routeKey의 시간 흐름을 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `path + occurredAt` raw 조합별 count를 먼저 구한다.
  2. `path -> routeKey`를 적용한다.
  3. `occurredAt -> timezone 기준 bucketStart`를 적용한다.
  4. 같은 `(routeKey, bucketStart)` 조합끼리 다시 합산한다.
  5. 요청 구간 안의 빈 bucket은 `0`으로 채운다.
- 전체 time-buckets와 같은 `bucket` 파라미터를 사용한다.
- `eventType` 필터가 있으면 해당 raw eventType 집합 안에서 route trend를 계산한다.

## event-type-time-buckets 정책
- canonical eventType 기준 time-bucket trend 집계
- raw eventType을 직접 노출하지 않고, eventType mapping 기준으로 정규화한 canonical eventType의 시간 흐름을 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `eventType + occurredAt` raw 조합별 count를 먼저 구한다.
  2. `rawEventType -> canonicalEventType`를 적용한다.
  3. `occurredAt -> timezone 기준 bucketStart`를 적용한다.
  4. 같은 `(canonicalEventType, bucketStart)` 조합끼리 다시 합산한다.
  5. 요청 구간 안의 빈 bucket은 `0`으로 채운다.
- 전체 time-buckets와 같은 `bucket` 파라미터를 사용한다.
- 매핑되지 않은 eventType은 `UNMAPPED_EVENT_TYPE`으로 포함한다.

## paths 정책
- raw path 기준 상세 집계
- 현재는 호환용으로 유지한다.
- 더 이상 확장 중심 API로 보지 않고, route 기반 집계로 점진적으로 옮겨간다.

## routes 정책
- routeKey 기준 상세 집계
- raw path를 직접 노출하는 대신 route template 기준으로 묶은 값을 보여준다.
- `top`은 raw path 단계가 아니라 routeKey 재집계 이후에 적용한다.

## routes/unique-users 정책
- routeKey 기준 unique user 상세 집계
- raw path를 직접 노출하지 않고, route template 기준으로 묶은 routeKey별 distinct `eventUser.id` 수를 보여준다.
- 현재 구현은 다음 순서로 계산한다.
  1. `path + eventUserId` raw 조합을 먼저 구한다.
  2. `path -> routeKey`를 적용한다.
  3. 같은 routeKey 안에서 distinct `eventUser.id` 수를 계산한다.
  4. `top`은 최종 routeKey unique user 결과에 적용한다.

## time-buckets 정책
- 시간 구간별 count 집계
- 현재 구현은 다음 순서로 계산한다.
  1. `occurredAt` raw 시각별 count를 먼저 구한다.
  2. `occurredAt -> timezone 기준 bucketStart`를 적용한다.
  3. 같은 `bucketStart`끼리 다시 합산한다.
  4. 요청 구간 안의 빈 bucket은 `0`으로 채운다.
- 현재는 전체 이벤트 기준 집계만 제공한다.

## 식별 사용자 / 익명 이벤트 정책
- 이번 단계에서는 익명 이벤트와 식별 이벤트를 같은 사용자 흐름으로 연결하지 않는다.
- 따라서 `uniqueUsers`, `funnel`, `retention`, `cohort`는 식별된 사용자 기준으로 해석한다.
- 익명 이벤트는 총량/경로/이벤트 타입 집계에는 포함되지만, user 기반 집계에는 포함하지 않는다.
- 후속 단계에서 `anonymousId`, `clientId`, `sessionId` 같은 연결 키 도입을 검토한다.

## 현재 제한
- `eventType` 필터가 있는 `overview.topEventTypes`는 아직 raw eventType 축을 그대로 따른다.
- routeKey × canonical eventType 교차 집계는 추가됐지만, 추가 필터/응답 구조 고도화는 아직 없다.
- route 기준 time-bucket trend와 canonical eventType 기준 time-bucket trend는 구현됐다.
- routeKey × canonical eventType 교차 시계열도 구현됐다.
- route template / eventType mapping 변경은 과거 이벤트 해석에도 즉시 영향을 준다.
- 변경 이력이나 versioning은 아직 없다.

## 현재 테스트 커버리지
- `EventCommandControllerIntegrationTest`
- `EventQueryControllerIntegrationTest`
- `EventQueryServiceTest`
- `RouteTemplateControllerIntegrationTest`
- `EventTypeMappingControllerIntegrationTest`
- `CanonicalEventTypeResolverTest`
- `RoutePathMatcherTest`
- `RouteKeyResolverTest`

## 회귀 검증 명령
- `./gradlew test --tests com.clickchecker.eventtype.service.CanonicalEventTypeResolverTest --tests com.clickchecker.eventtype.controller.EventTypeMappingControllerIntegrationTest --tests com.clickchecker.event.controller.EventQueryControllerIntegrationTest --tests com.clickchecker.event.controller.EventCommandControllerIntegrationTest --tests com.clickchecker.event.service.EventQueryServiceTest --tests com.clickchecker.route.controller.RouteTemplateControllerIntegrationTest --tests com.clickchecker.route.service.RoutePathMatcherTest --tests com.clickchecker.route.service.RouteKeyResolverTest`
