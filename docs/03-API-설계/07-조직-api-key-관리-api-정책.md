# 조직 API Key 관리 API 정책 (18단계 1차)

## 목적
- organization 생성 이후 바로 이벤트 수집/분석 API를 사용할 수 있도록, admin 경로 기준의 API key 발급/조회/rotate 계약을 정리한다.
- 기존 `X-API-Key` 인증 정책은 유지하되, plain key를 언제 어떻게 1회 노출하는지와 role 경계를 명확히 한다.

## 현재 엔드포인트
- `POST /api/v1/admin/organizations`
- `GET /api/v1/admin/organizations/{organizationId}/api-key`
- `POST /api/v1/admin/organizations/{organizationId}/api-key/rotate`

## 공통 경계
- organization API key는 여전히 machine credential이다.
- `/api/events/**`, `/api/v1/events/**`는 계속 `X-API-Key`로 organization scope를 결정한다.
- `/api/v1/admin/**`는 JWT로 보호되는 account 기반 관리자 경로다.
- 즉 "계정 로그인"과 "조직 API key"는 같은 토큰이 아니다.

## 저장 정책
- API key 평문은 DB에 저장하지 않는다.
- `organizations`에는 아래 메타데이터만 저장한다.
  - `apiKeyKid`
  - `apiKeyHash`
  - `apiKeyPrefix`
  - `apiKeyStatus`
  - `apiKeyCreatedAt`
  - `apiKeyRotatedAt`
  - `apiKeyLastUsedAt`
- plain key는 조직 생성 응답 또는 rotate 응답에서만 1회 반환한다.

## organization 생성 + 초기 key 발급

### 엔드포인트
- `POST /api/v1/admin/organizations`

### 권한
- 로그인된 account

### 요청
```json
{
  "name": "Acme"
}
```

### 성공 응답
```json
{
  "organizationId": 1,
  "name": "Acme",
  "ownerMembershipId": 10,
  "apiKey": "ck_test_v1_...",
  "apiKeyPrefix": "abcd1234"
}
```

### 동작 요약
- organization을 생성한다.
- 요청 account를 첫 `OWNER` membership으로 연결한다.
- 초기 plain API key를 1회 발급한다.
- 즉 `signup -> organization create -> apiKey 수령`이 바로 이어진다.

## 현재 key 메타데이터 조회

### 엔드포인트
- `GET /api/v1/admin/organizations/{organizationId}/api-key`

### 권한
- `OWNER`, `ADMIN`

### 성공 응답
```json
{
  "kid": "abcd1234567890",
  "apiKeyPrefix": "abcd1234",
  "status": "ACTIVE",
  "createdAt": "2026-03-18T10:00:00Z",
  "rotatedAt": "2026-03-18T10:00:00Z",
  "lastUsedAt": "2026-03-18T10:05:12Z"
}
```

### 정책
- plain `apiKey`는 다시 보여주지 않는다.
- 메타데이터 조회는 조직의 현재 자격증명 상태 확인 용도다.

## API key rotate

### 엔드포인트
- `POST /api/v1/admin/organizations/{organizationId}/api-key/rotate`

### 권한
- `OWNER`

### 성공 응답
```json
{
  "apiKey": "ck_test_v1_...",
  "apiKeyPrefix": "f0a1b2c3",
  "rotatedAt": "2026-03-18T11:00:00Z"
}
```

### 동작 요약
- 새 plain API key를 발급한다.
- organization의 `kid/hash/prefix`를 새 값으로 교체한다.
- old key는 즉시 무효화된다.
- new key만 이후 이벤트 수집/분석 API에서 유효하다.

## 권한 정책
- organization 생성 직후 초기 key 발급
  - creator `OWNER` 흐름 안에서 처리
- key 메타데이터 조회
  - `OWNER`, `ADMIN`
- key rotate
  - `OWNER`
- `VIEWER`
  - key 메타데이터 조회/rotate 모두 불가

## 에러 정책
- `400`
  - organization 생성 요청 형식 오류
- `401`
  - admin JWT 누락/무효
  - old key 또는 무효 key로 이벤트 API 호출
- `403`
  - organization membership 없음
  - role 부족 (`VIEWER`의 메타 조회, `ADMIN`의 rotate)
- `404`
  - organization 없음

## 기존 공개 organization 생성 경로와의 관계
- 기존 `POST /api/organizations`는 아직 남아 있다.
- 다만 포트폴리오 기준 주 경로는 `POST /api/v1/admin/organizations`로 본다.
- 공개 경로 정리/제거는 후속 단계에서 다룬다.

## 현재 제한
- organization당 활성 API key는 1개만 지원한다.
- key별 scope / 권한 / 만료일 정책은 없다.
- key 이력 목록은 제공하지 않는다.
- key disable / enable은 아직 없다.
- admin 콘솔의 key 관리 화면은 후속 단계에서 다룬다.
