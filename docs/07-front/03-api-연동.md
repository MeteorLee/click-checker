# API 연동 (v1.4)

## 목표
- 프런트에서 실제로 사용하는 API 계약을 현재 구현 상태 기준으로 정리한다.
- 문서는 `회원가입/로그인 -> organization 선택/생성 -> analytics 상세 -> 설정/운영 화면` 흐름에 필요한 API만 다룬다.

---

## 1. 범위

### 포함
- `POST /api/v1/admin/auth/signup`
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/me`
- `POST /api/v1/admin/organizations`
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
- refresh / logout
- 이메일/토큰 기반 초대 수락
- organization 상태 모델

---

## 2. 공통 원칙

### 인증
- 브라우저는 admin JWT만 사용한다.
- `Authorization: Bearer <accessToken>` 헤더를 사용한다.
- API key는 브라우저 인증에 사용하지 않는다.

### 로컬 개발
- frontend: `http://localhost:3001`
- backend: `http://localhost:8080`

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

### organization 생성
- `POST /api/v1/admin/organizations`
- 응답 핵심:
  - `organizationId`
  - `name`
  - `ownerMembershipId`
  - `apiKey`
  - `apiKeyPrefix`

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

### activity
- `GET /api/v1/admin/organizations/{organizationId}/analytics/activity`

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
