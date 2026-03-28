# API 연동 (v1.6)

## 목표
- 프런트에서 실제로 사용하는 API 계약을 현재 구현 상태 기준으로 정리한다.
- 문서는 `회원가입/로그인 -> organization 선택/생성 -> analytics 상세 -> 설정/운영 화면`과 제품 API 가이드 화면에서 직접 다루는 API를 함께 정리한다.

---

## 1. 범위

### 포함
- `POST /api/v1/admin/auth/signup`
- `POST /api/v1/admin/auth/login`
- `POST /api/v1/admin/auth/refresh`
- `GET /api/v1/admin/me`
- `POST /api/v1/admin/organizations`
- `POST /api/v1/admin/organizations/demo/join`
- `DELETE /api/v1/admin/organizations/{organizationId}/members/membership`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/routes`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/event-types`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/trends`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/users`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/activity`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/retention`
- `POST /api/v1/admin/organizations/{organizationId}/analytics/funnels/report`
- `GET /api/v1/admin/organizations/{organizationId}/api-key`
- `POST /api/v1/admin/organizations/{organizationId}/api-key/rotate`
- `GET/POST/PUT/DELETE /api/v1/admin/organizations/{organizationId}/route-templates`
- `GET/POST/PUT/DELETE /api/v1/admin/organizations/{organizationId}/event-type-mappings`
- `GET /api/v1/admin/organizations/{organizationId}/members`
- `POST /api/v1/admin/organizations/{organizationId}/members/by-login-id`
- `PUT /api/v1/admin/organizations/{organizationId}/members/{memberId}/role`
- `DELETE /api/v1/admin/organizations/{organizationId}/members/{memberId}`

### 제외
- logout
- 이메일/토큰 기반 초대 수락
- organization 상태 모델

### 참고: 제품 API
- 브라우저는 admin JWT API를 사용하지만, 제품/API key 기준 공개 분석 API도 별도로 존재한다.
- 현재 공개 분석 API에서 프런트 작업과 직접 맞닿은 경로:
  - `GET /api/v1/events/analytics/aggregates/overview`
  - `GET /api/v1/events/analytics/activity`
  - `GET /api/v1/events/analytics/users/overview`
  - `GET /api/v1/events/analytics/retention/*`
  - `POST /api/v1/events/analytics/funnels/report`

---

## 2. 공통 원칙

### 인증
- 브라우저는 admin JWT만 사용한다.
- `Authorization: Bearer <accessToken>` 헤더를 사용한다.
- API key는 브라우저 인증에 사용하지 않는다.
- access token 만료 시 프런트는 `POST /api/v1/admin/auth/refresh`로 자동 재발급을 시도한다.

### 로컬 개발
- frontend: `http://localhost:3001`
- backend: `http://localhost:8080`
- dashboard 하위 화면은 좌측 사이드바를 공통 탐색으로 사용한다.
- 제품 API 가이드 하위 화면도 별도 좌측 사이드바를 사용한다.

### 날짜 처리
- analytics 요청은 `from`, `to`를 날짜 문자열로 보낸다.
- timezone 파라미터는 보내지 않는다.
- 백엔드는 `Asia/Seoul` 기준으로 해석한다.
- analytics 조회 기간은 최대 90일이다.

### 에러 처리
- `401`: 로그인 해제 후 `/login`
- `403`: 권한 부족 상태 표시
- `404`: organization 없음 상태 표시
- `405`: local `bootRun`이 최신 코드가 아닐 수 있으므로 재기동 확인

---

## 3. 인증 / organization

### 회원가입
- `POST /api/v1/admin/auth/signup`

### 로그인
- `POST /api/v1/admin/auth/login`

### 현재 사용자
- `GET /api/v1/admin/me`
- memberships 목록:
  - `organizationId`
  - `organizationName`
  - `role`

### access token 재발급
- `POST /api/v1/admin/auth/refresh`
- 설명:
  - 프런트는 `401`을 받으면 refresh token으로 access token 재발급을 시도한다.
  - refresh도 실패하면 `/login`으로 보낸다.

### organization 생성
- `POST /api/v1/admin/organizations`
- 응답 핵심:
  - `organizationId`
  - `name`
  - `ownerMembershipId`
  - `apiKey`
  - `apiKeyPrefix`
- 프런트 활용:
  - organization 생성 직후 API key 1회 표시
  - Quick Start에서는 테스트용 organization 생성에 사용

### demo organization 추가
- `POST /api/v1/admin/organizations/demo/join`
- 설명:
  - seed로 준비된 `demo_web_shop`을 현재 계정 목록에 추가한다.
  - 이미 추가된 경우 중복 없이 그대로 성공 처리한다.

### organization 삭제(내 목록에서 제거)
- `DELETE /api/v1/admin/organizations/{organizationId}/members/membership`
- 설명:
  - UI는 `삭제`
  - 실제로는 membership 해제
  - 혼자 남은 OWNER인 경우 확인 문구 입력이 필요할 수 있음

---

## 4. analytics

### overview
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`
- 파라미터:
  - `from`
  - `to`
- 설명:
  - overview는 핵심 데이터만 보여준다.
  - API key 관리는 별도 settings page로 분리했다.
  - Quick Start는 admin overview가 아니라 공개 제품 API `GET /api/v1/events/analytics/aggregates/overview`를 직접 사용한다.

### routes
- `GET /api/v1/admin/organizations/{organizationId}/analytics/routes`
- 파라미터:
  - `from`
  - `to`
  - `top`

### event types
- `GET /api/v1/admin/organizations/{organizationId}/analytics/event-types`
- 파라미터:
  - `from`
  - `to`
  - `top`

### trends
- `GET /api/v1/admin/organizations/{organizationId}/analytics/trends`
- 파라미터:
  - `from`
  - `to`
  - `bucket`
- 규칙:
  - `DAY` 또는 `HOUR`
  - `HOUR`는 하루 전용

### users
- `GET /api/v1/admin/organizations/{organizationId}/analytics/users`
- 응답 핵심:
  - `totalEvents`
  - `identifiedEvents`
  - `anonymousEvents`
  - `identifiedUsers`
  - `newUsers`
  - `returningUsers`
  - `newUserEvents`
  - `returningUserEvents`
  - `avgEventsPerIdentifiedUser`

### activity
- `GET /api/v1/admin/organizations/{organizationId}/analytics/activity`
- 응답 핵심:
  - `weekdaySummary`
  - `weekendSummary`
  - `dayOfWeekDistribution`
  - `weekdayHourlyDistribution`
  - `weekendHourlyDistribution`
- 같은 형태의 공개 API:
  - `GET /api/v1/events/analytics/activity`
  - `timezone` 파라미터를 받아 API key 기준 조직의 activity 분포를 조회할 수 있다.

### retention
- `GET /api/v1/admin/organizations/{organizationId}/analytics/retention`
- 파라미터:
  - `from`
  - `to`
  - `days`
- 규칙:
  - `정확히 N일 뒤`가 아니라 `N일 내 재방문`

### funnels
- `POST /api/v1/admin/organizations/{organizationId}/analytics/funnels/report`
- 요청 본문 핵심:
  - `from`
  - `to`
  - `conversionWindowDays`
  - `steps[]`

---

## 5. API key / rules / members

### API key
- `GET /api/v1/admin/organizations/{organizationId}/api-key`
- `POST /api/v1/admin/organizations/{organizationId}/api-key/rotate`
- 프런트 경로:
  - `/dashboard/[organizationId]/api-key`

### route templates
- `GET/POST/PUT/DELETE /api/v1/admin/organizations/{organizationId}/route-templates`

### event type mappings
- `GET/POST/PUT/DELETE /api/v1/admin/organizations/{organizationId}/event-type-mappings`

### members
- `GET /api/v1/admin/organizations/{organizationId}/members`
- `POST /api/v1/admin/organizations/{organizationId}/members/by-login-id`
- `PUT /api/v1/admin/organizations/{organizationId}/members/{memberId}/role`
- `DELETE /api/v1/admin/organizations/{organizationId}/members/{memberId}`

설명:
- 멤버 초대는 이메일/토큰 수락이 아니라, 기존 계정을 `loginId` 기준으로 바로 membership에 추가하는 방식이다.
- 멤버 목록 조회는 `VIEWER`도 가능하다.
- 역할 변경과 멤버 제거는 `OWNER`만 가능하다.

---

## 6. 제품 API 가이드 화면과 연결되는 공개 API

### Quick Start
- `POST /api/events`
- `GET /api/v1/events/analytics/aggregates/overview`
- 규칙:
  - API key를 사용자가 직접 입력한다.
  - 이벤트 전송 뒤 overview는 버튼을 눌렀을 때만 조회한다.
  - overview 조회 범위는 대시보드 overview와 같은 7일 기준을 사용한다.

### 이벤트 전송 가이드
- `POST /api/events`
- 필수 필드:
  - `eventType`
  - `path`
- 선택 필드:
  - `occurredAt`
  - `externalUserId`
  - `payload`

### 집계 API 가이드
- `GET /api/v1/events/analytics/aggregates/overview`
- `GET /api/v1/events/analytics/activity`
- `GET /api/v1/events/analytics/users/overview`
- `GET /api/v1/events/analytics/retention/daily`
- `GET /api/v1/events/analytics/retention/matrix`
- `POST /api/v1/events/analytics/funnels/report`

### 데이터 정규화 가이드
- 공개 API 경로 자체를 추가로 호출하지는 않는다.
- 대신 아래 개념을 설명한다.
  - raw path -> route key
  - raw event type -> 이벤트 공통 키
  - `UNMATCHED_ROUTE`
  - `UNMAPPED_EVENT_TYPE`
