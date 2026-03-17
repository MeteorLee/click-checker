# 확장 분석 API 정책 (15단계 1차)

## 목적
- 14단계에서 정리한 핵심 집계 기반 위에 사용자 분석, funnel, retention API를 올린다.
- 이번 문서는 확장 분석 API의 계산 규칙과 응답 계약을 먼저 고정하는 데 목적이 있다.
- 성능 최적화나 identity 병합보다, 해석 가능한 최소 분석 API를 만드는 것을 우선한다.

## 현재 엔드포인트
- `GET /api/v1/events/analytics/users/overview`
- `POST /api/v1/events/analytics/funnels/report`
- `GET /api/v1/events/analytics/retention/daily`
- `GET /api/v1/events/analytics/retention/matrix`

## 예정 엔드포인트
- cohort 상세/확장 API

## 공통 인증 / 스코프
- 보호 대상:
  - `/api/events/**`
  - `/api/v1/events/**`
- 조직 스코프는 `X-API-Key -> authOrgId`로만 결정한다.
- 확장 분석 API도 `organizationId` 쿼리를 받지 않는다.

## 공통 시간 / 사용자 기준
- `from`, `to`
  - 필수
  - 기본 기준: `from <= occurredAt < to`
  - `Instant` 절대시간 범위로 해석한다.
  - 단, funnel은 `step1 anchor`는 `from <= occurredAt < to`에서 찾고, 후속 step 탐색은 기본 `7일` 또는 요청 `conversionWindowDays`만큼 lookahead 한다.
- `timezone`
  - funnel / retention / cohort 응답 메타와 날짜 해석에 사용한다.
  - 특히 cohort date와 retention day bucket은 요청 timezone 기준 local date로 해석한다.
- 사용자 기준
  - 기본 사용자 기준은 `organization` 범위 내 `eventUser.id`
  - `eventUser`가 없는 이벤트는 확장 분석 대상에서 제외한다.
  - anonymous 포함 분석, login 병합, alias merge는 이번 단계에서 다루지 않는다.

## users/overview 정책

### 목적
- 조회 구간 내 식별 사용자 활동을 사용자 중심으로 요약한다.
- 14단계 overview가 이벤트 중심 요약이었다면, 이 API는 사용자 생애주기 요약에 가깝다.

### 엔드포인트
- `GET /api/v1/events/analytics/users/overview`

### 쿼리 파라미터
- `from`
- `to`
- `externalUserId` (선택)

### 응답 항목
- `identifiedUsers`
- `newUsers`
- `returningUsers`
- `avgEventsPerIdentifiedUser`

### 계산 규칙
- `identifiedUsers`
  - 조회 구간 내 활동한 식별 사용자 수
- `newUsers`
  - 조회 구간 내 활동했고, `firstSeen`도 조회 구간 내인 사용자 수
- `returningUsers`
  - 조회 구간 내 활동했고, `firstSeen`은 조회 구간 이전인 사용자 수
- `avgEventsPerIdentifiedUser`
  - 조회 구간 내 식별 사용자 기준 평균 이벤트 수
  - `total identified events / identifiedUsers`
  - 분모가 0이면 `null`

### firstSeen 정의
- `firstSeen`은 “조회 구간 내 첫 이벤트”가 아니다.
- `organization` 범위에서 해당 사용자의 전체 이벤트 중 earliest `occurredAt`이다.
- 조회 구간은 현재 활동 여부를 제한할 뿐, `firstSeen` 자체를 다시 계산하지 않는다.

## funnel 정책

### 목적
- 식별 사용자 기준 전환 흐름을 step 순서대로 계산한다.

### 엔드포인트
- `POST /api/v1/events/analytics/funnels/report`

### 요청 항목
- `from`
- `to`
- `externalUserId` (선택)
- `steps`
  - step 정의 목록
  - 최소 2개, 최대 4개
  - 각 step은
    - `canonicalEventType` 필수
    - `routeKey` 선택
- `conversionWindowDays` (선택)
  - 없으면 기본값 `7`
  - `1~365` 범위의 일(day) 단위 값

### 응답 항목
- `steps`
- `conversionWindow`
- `items[].stepOrder`
- `items[].step.canonicalEventType`
- `items[].step.routeKey`
- `items[].users`
- `items[].conversionRateFromFirstStep`
- `items[].previousStepUsers`
- `items[].conversionRateFromPreviousStep`
- `items[].dropOffUsersFromPreviousStep`

### 최소 지원 범위
- 2~4 step
- step 정의는 `canonicalEventType + optional routeKey`
- 같은 사용자 기준 계산
- conversion window 기본값은 `7일`
- 요청에서 `conversionWindowDays`로 변경 가능

### step 정의 단위
- 현재 step은 아래 형태를 지원한다.
  - `canonicalEventType = X`
  - `canonicalEventType = X` and `routeKey = Y`
- `routeKey` 비교는 raw path가 아니라 route 정규화 결과를 기준으로 한다.
- 즉 실제 계산 시에는 raw path를 먼저 `routeKey`로 해석한 뒤 step 조건과 비교한다.

### anchor / 순서 규칙
- 각 사용자는 step1의 최초 발생 시점을 anchor로 삼는다.
- step1 anchor는 `from <= occurredAt < to` 범위에서만 찾는다.
- anchor 이전에 발생한 step2~N 이벤트는 계산에 포함하지 않는다.
- 각 step은 직전 인정 step 시각 이상에서 조건을 만족하는 최초 이벤트 1건만 인정한다.
- 같은 step 이벤트를 여러 번 발생시켜도 가장 이른 인정 1건만 사용한다.
- step2 없이 step3만 발생한 사용자는 step3 전환자로 인정하지 않는다.

### conversion window 적용 방식
- 최소 버전의 기본 conversion window는 `7일`이다.
- 요청에 `conversionWindowDays`가 있으면 그 값을 우선 사용한다.
- 후속 step은 anchor 시각부터 기본 `7일` 또는 요청 `conversionWindowDays` 이내에서만 인정한다.
- 따라서 실제 계산 시에는 `to` 이후 이벤트도 최대 해당 conversion window만큼 lookahead 하여 step2~N를 판정할 수 있다.
- 단, step1 anchor 자체는 항상 요청 구간(`from ~ to`) 안에서만 잡는다.

### step별 해석 보조 값
- `conversionRateFromFirstStep`
  - step1 진입자 대비 현재 step 도달 비율
- `step.routeKey`
  - 지정되면 해당 routeKey에서 발생한 이벤트만 step 후보로 인정한다.
  - 비어 있으면 route 조건 없이 canonicalEventType만 비교한다.
- `previousStepUsers`
  - 현재 step 바로 직전 step의 사용자 수
- `conversionRateFromPreviousStep`
  - 직전 step 사용자 대비 현재 step 도달 비율
- `dropOffUsersFromPreviousStep`
  - 직전 step 사용자 중 현재 step에 도달하지 못한 사용자 수
- step1에는 직전 step이 없으므로 위 3개 값은 `null`이다.

### same timestamp 규칙
- 같은 timestamp의 이벤트는 허용한다.
- 후속 step은 이전 step과 `같거나 이후` 시각이면 인정한다.
- 동일 timestamp에서는 step 순서 위반으로 보지 않으며, 미세 순서 재구성은 시도하지 않는다.

## retention / cohort 정책

### 목적
- 사용자의 최초 유입 cohort를 기준으로 Day N 재방문율을 계산한다.

### 엔드포인트
- `GET /api/v1/events/analytics/retention/daily`
- `GET /api/v1/events/analytics/retention/matrix`

### 쿼리 파라미터
- `from`
- `to`
- `timezone`
- `externalUserId` (선택)
- `minCohortUsers` (daily / matrix에서 선택)

### 응답 항목
- `timezone`
- `items[].cohortDate`
- `items[].cohortUsers`
- `items[].day1Users`
- `items[].day1RetentionRate`
- `items[].day7Users`
- `items[].day7RetentionRate`
- `items[].day30Users`
- `items[].day30RetentionRate`

### matrix 응답 항목
- `days`
- `items[].cohortDate`
- `items[].cohortUsers`
- `items[].values[].day`
- `items[].values[].users`
- `items[].values[].retentionRate`

### 최소 지원 범위
- daily cohort만 지원
- Day 1 / 7 / 30 retention
- exact-day retention만 지원

### firstSeen 정의
- `firstSeen`은 `organization` 범위 내 해당 사용자의 전체 이벤트 중 earliest `occurredAt`
- 조회 구간은 cohort 관찰 범위를 제한할 뿐, `firstSeen` 자체를 바꾸지 않는다.

### cohort / 날짜 해석
- cohort date는 요청 `timezone` 기준 local date로 계산한다.
- `firstSeen` timestamp 자체는 raw event timestamp를 기준으로 보되, cohort 분류는 timezone 적용 후 local date를 사용한다.

### Day N retention 정의
- Day N retention은 cohort 기준일로부터 N일 후의 동일 local date bucket에 활동이 있는 사용자의 비율이다.
- `on or after` 방식은 이번 단계에서 지원하지 않는다.

### 구현 방식
- cohort 사용자는 `firstSeen`이 요청 구간(`from <= firstSeen < to`) 안에 있는 식별 사용자만 포함한다.
- `cohortDate`는 `firstSeen`을 요청 `timezone`으로 변환한 local date다.
- 활동 여부는 조회 구간 이후 최대 30일을 추가 조회해 exact-day 기준으로 판정한다.
- 각 retention 비율은 `retainedUsers / cohortUsers`로 계산한다.
- `minCohortUsers`가 있으면 해당 값보다 작은 cohort는 응답에서 제외한다.
- `minCohortUsers`가 없으면 기본값은 `1`이다.

### matrix 규칙
- `matrix`는 `days` 쿼리로 custom day 목록을 받는다.
- `days`가 없으면 기본값은 `1, 7, 30`이다.
- `days`는 중복 제거 후 오름차순으로 정규화한다.
- 각 day 값은 `1~365` 범위만 허용하고, 최대 10개까지 지원한다.
- `matrix`도 exact-day / timezone local date 규칙은 `daily`와 동일하다.

## 응답 구조 정책

### 현재 상태
- 이번 단계에서는 전 API 공통 `meta / summary / data` envelope를 강제하지 않는다.
- users / funnel / retention은 각 API 의미가 바로 읽히는 현재 응답 구조를 유지한다.
- 이번 단계의 제품화 우선순위는 응답 형식 통일보다는 아래에 둔다.
  - 계산 규칙 고정
  - `/api/v1/events/analytics/...` 공개 URI 일관성
  - 버저닝 원칙 문서화

## 버저닝 정책
- 확장 분석 API는 `/api/v1` 아래에서 시작한다.
- breaking change는 `/api/v2`로 분리한다.

### breaking change 예시
- 필드 제거 / 이름 변경
- 기존 필드 의미 변경
- 응답 구조 중첩 변경
- 기본 계산 규칙 변경

### non-breaking change 예시
- nullable 신규 필드 추가
- 새로운 선택 필터 추가
- 새로운 endpoint 추가

## 현재 제한
- `users/overview`는 현재 `externalUserId` 필터만 지원한다.
- funnel은 현재 `canonicalEventType + optional routeKey` step까지 지원한다.
- funnel의 `conversionWindowDays`는 현재 `1~365` 범위만 지원한다.
- retention은 현재 daily cohort + Day 1/7/30 exact-day만 지원한다.
- retention `matrix`는 custom day 목록을 지원하지만, cohort 상세 drill-down이나 on-or-after 방식은 아직 지원하지 않는다.
- anonymous 포함 사용자 분석과 identity 병합은 이번 단계 범위 밖이다.
