# 계정 인증 API 정책 (16단계 1차)

## 목적
- 로그인 가능한 사용자 계정 기반의 최소 인증 API를 정의한다.
- 기존 `X-API-Key` 기반 organization 인증과, 계정 기반 JWT 인증의 경계를 분리한다.
- 이번 문서는 현재 구현된 `login / refresh / logout / me` 계약을 고정하는 데 목적이 있다.

## 현재 엔드포인트
- `POST /api/v1/admin/auth/login`
- `POST /api/v1/admin/auth/refresh`
- `POST /api/v1/admin/auth/logout`
- `GET /api/v1/admin/me`

## 공통 인증 / 경계
- `/api/v1/admin/**`
  - 계정 기반 JWT 보호 경로다.
- 예외 경로
  - `POST /api/v1/admin/auth/login`
  - `POST /api/v1/admin/auth/refresh`
  - `POST /api/v1/admin/auth/logout`
- 기존 `/api/events/**`, `/api/v1/events/**`
  - 계속 `X-API-Key` 인증 대상이다.
- 즉 계정 인증과 organization 인증은 같은 의미로 합치지 않는다.

## 토큰 정책

### access token
- 형식: JWT
- 용도: 보호된 `/api/v1/admin/**` API 호출
- 전달: `Authorization: Bearer <accessToken>`
- 현재 만료 시간 기본값: `900초 (15분)`
- claim 최소 범위:
  - `sub = accountId`
  - `type = access`

### refresh token
- 형식: 난수 기반 opaque token
- 용도: access token 재발급 전용
- 전달: request body의 `refreshToken`
- 현재 만료 시간 기본값: `1209600초 (14일)`
- 서버 저장:
  - 원문은 저장하지 않는다.
  - `SHA-256` hash만 `refresh_tokens`에 저장한다.

### rotation 정책
- `refresh` 성공 시 기존 refresh token은 즉시 `revoked` 처리한다.
- 재발급 응답에는 새 access token과 새 refresh token을 함께 반환한다.
- 이미 `revoked`된 refresh token을 다시 사용하면 `401`로 거부한다.

## login 정책

### 엔드포인트
- `POST /api/v1/admin/auth/login`

### 요청
```json
{
  "loginId": "alice",
  "password": "secret123!"
}
```

### 검증 규칙
- `loginId`, `password`는 둘 다 필수다.
- `loginId`가 없거나 비밀번호가 틀리면 `401`
- `DISABLED` 계정도 외부 응답은 `401`

### 성공 응답
```json
{
  "accountId": 1,
  "accessToken": "<jwt>",
  "accessTokenExpiresIn": 900,
  "refreshToken": "<refresh-token>",
  "refreshTokenExpiresIn": 1209600
}
```

### 동작 요약
- `loginId`로 계정을 조회한다.
- `BCryptPasswordEncoder`로 비밀번호를 검증한다.
- 성공 시 access token과 refresh token을 함께 발급한다.
- refresh token의 hash를 DB에 저장한다.

## refresh 정책

### 엔드포인트
- `POST /api/v1/admin/auth/refresh`

### 요청
```json
{
  "refreshToken": "<refresh-token>"
}
```

### 성공 응답
```json
{
  "accountId": 1,
  "accessToken": "<new-jwt>",
  "accessTokenExpiresIn": 900,
  "refreshToken": "<new-refresh-token>",
  "refreshTokenExpiresIn": 1209600
}
```

### 검증 규칙
- body의 `refreshToken`은 필수다.
- 서버는 요청 받은 refresh token을 hash로 바꿔 조회한다.
- 아래 경우는 모두 `401`이다.
  - 존재하지 않는 refresh token
  - 이미 `revoked`된 refresh token
  - 만료된 refresh token
  - `DISABLED` 계정에 연결된 refresh token

### 동작 요약
- 기존 refresh token은 `lastUsedAt` 기록 후 `revoked` 처리한다.
- 새 access token과 새 refresh token을 발급한다.
- 새 refresh token의 hash를 DB에 저장한다.

## logout 정책

### 엔드포인트
- `POST /api/v1/admin/auth/logout`

### 요청
```json
{
  "refreshToken": "<refresh-token>"
}
```

### 성공 응답
- `204 No Content`

### 검증 규칙
- body의 `refreshToken`은 필수다.
- 존재하지 않거나 이미 폐기됐거나 만료된 refresh token은 `401`

### 동작 요약
- 현재 refresh token 하나를 폐기한다.
- 성공 후 같은 refresh token으로 다시 `refresh`를 호출하면 `401`이 난다.
- 최소 버전에서는 access token 없이 refresh token만으로 logout을 처리한다.

## me 정책

### 엔드포인트
- `GET /api/v1/admin/me`

### 요청 헤더
- `Authorization: Bearer <accessToken>`

### 성공 응답
```json
{
  "accountId": 1,
  "loginId": "alice",
  "status": "ACTIVE"
}
```

### 검증 규칙
- access token이 없으면 `401`
- access token이 잘못됐거나 만료됐으면 `401`
- 토큰은 유효하지만 계정이 `DISABLED`면 `403`

### 동작 요약
- JWT에서 `accountId`를 꺼낸다.
- DB에서 계정을 다시 조회한다.
- 현재 로그인 상태 확인용 최소 API로 사용한다.

## 에러 정책
- `400`
  - 필수 필드 누락 / blank
- `401`
  - loginId/password 불일치
  - invalid refresh token
  - access token 누락/무효
- `403`
  - access token은 유효하지만 계정이 `DISABLED`

## 현재 제한
- 계정과 조직의 관계(`OrganizationMember`)는 아직 없다.
- OWNER / ADMIN / VIEWER 같은 RBAC은 아직 없다.
- 회원가입 / 이메일 인증 / 비밀번호 찾기는 지원하지 않는다.
- refresh token은 현재 HttpOnly cookie가 아니라 request body로 받는다.
- logout은 현재 refresh token 단위 폐기만 지원한다.
- JWT 필터 단계의 `401/403` 응답 바디 표준화는 후속 과제다.
