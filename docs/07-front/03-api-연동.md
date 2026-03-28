# API 연동 (v1.5)

## 목표
- 프런트에서 실제로 사용하는 API 계약을 현재 구현 상태 기준으로 정리한다.
- 문서는 `회원가입/로그인 -> organization 선택/생성 -> analytics 상세 -> 설정/운영 화면` 흐름에 필요한 API만 다룬다.

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
