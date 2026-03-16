# 확장 분석 API 정책 (15단계 1차)

## 목적
- 14단계에서 정리한 핵심 집계 기반 위에 사용자 분석, funnel, retention API를 올린다.
- 이번 문서는 확장 분석 API의 계산 규칙과 응답 계약을 먼저 고정하는 데 목적이 있다.
- 성능 최적화나 identity 병합보다, 해석 가능한 최소 분석 API를 만드는 것을 우선한다.

## 현재 엔드포인트
- `GET /api/v1/events/analytics/users/overview`

## 예정 엔드포인트
- `POST /api/v1/events/analytics/funnels/report`
- `GET /api/v1/events/analytics/retention/daily`

## 공통 인증 / 스코프
- 보호 대상:
  - `/api/events/**`
  - `/api/v1/events/**`
- 조직 스코프는 `X-API-Key -> authOrgId`로만 결정한다.
- 확장 분석 API도 `organizationId` 쿼리를 받지 않는다.

## 공통 시간 / 사용자 기준
- `from`, `to`
  - 필수
  - 기준: `from <= occurredAt < to`
  - `Instant` 절대시간 범위로 해석한다.
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

### 최소 지원 범위
- 2~4 step
- step 정의는 `canonicalEventType only`
- 같은 사용자 기준 계산
- 기본 conversion window는 `7일`

### step 정의 단위
- 15단계 최소 step은 `canonicalEventType = X` 형태로만 지원한다.
- `canonicalEventType + routeKey` 조합 step은 후속 확장으로 둔다.

### anchor / 순서 규칙
- 각 사용자는 step1의 최초 발생 시점을 anchor로 삼는다.
- anchor 이전에 발생한 step2~N 이벤트는 계산에 포함하지 않는다.
- 각 step은 직전 인정 step 시각 이상에서 조건을 만족하는 최초 이벤트 1건만 인정한다.
- 같은 step 이벤트를 여러 번 발생시켜도 가장 이른 인정 1건만 사용한다.
- step2 없이 step3만 발생한 사용자는 step3 전환자로 인정하지 않는다.

### same timestamp 규칙
- 같은 timestamp의 이벤트는 허용한다.
- 후속 step은 이전 step과 `같거나 이후` 시각이면 인정한다.
- 동일 timestamp에서는 step 순서 위반으로 보지 않으며, 미세 순서 재구성은 시도하지 않는다.

## retention / cohort 정책

### 목적
- 사용자의 최초 유입 cohort를 기준으로 Day N 재방문율을 계산한다.

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

## 응답 구조 정책

### 현재 상태
- users/overview는 우선 단순 응답 record를 사용한다.
- 최소 구현을 빠르게 닫기 위해 `meta / summary / data` 3분할을 즉시 강제하지는 않는다.

### 목표 방향
- 확장 분석 API는 점진적으로 아래 구조를 공통 틀로 맞춘다.
  - `meta`
  - `summary`
  - `data`

### appliedFilters 예시
```json
{
  "organizationId": 1,
  "from": "2026-03-01T00:00:00Z",
  "to": "2026-03-08T00:00:00Z",
  "timezone": "Asia/Seoul",
  "externalUserId": null,
  "canonicalEventTypes": ["SIGN_UP", "PURCHASE"],
  "routeKey": null
}
```

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
- funnel은 아직 구현 전이며, canonical eventType only step 계약만 고정한 상태다.
- retention / cohort도 아직 구현 전이며, exact-day / timezone 기준만 먼저 문서로 고정한 상태다.
- anonymous 포함 사용자 분석과 identity 병합은 이번 단계 범위 밖이다.
