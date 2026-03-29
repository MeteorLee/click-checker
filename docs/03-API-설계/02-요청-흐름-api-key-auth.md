# 요청 흐름: API Key 인증 (현재 기준)

## 대상 요청
- `POST /api/events`
- `GET /api/v1/events/analytics/**`

## 처리 순서
1. `eventSecurityFilterChain`
   - `/api/events/**`, `/api/v1/events/**` 요청만 이 체인으로 라우팅한다.
   - 이 체인에서는 인증된 요청만 허용한다.

2. `ApiKeyAuthFilter`
   - `X-API-Key` 헤더를 검증한다.
   - 누락/무효 시 `401` 반환 후 종료한다.
   - 성공 시 `ApiKeyPrincipal`을 `SecurityContext`에 저장한다.

3. Controller
   - `@AuthenticationPrincipal ApiKeyPrincipal principal`을 받는다.
   - `principal.organizationId()`를 사용한다.
   - 요청 payload/query의 `organizationId`를 사용하지 않는다.
   - 서비스에 인증된 organization id를 전달한다.

4. Service
   - 인증된 organization id로 Organization을 조회한다.
   - 이벤트 저장/조회를 organization 스코프로 수행한다.

## EventUser 연결 규칙 (수집 시)
- `externalUserId`가 있으면 `(authOrgId, externalUserId)`로 조회한다.
- 없으면 새 `EventUser`를 생성해 연결한다.
- `externalUserId`가 없으면 익명 이벤트(`eventUser=null`)로 저장한다.

## 실패 응답 요약
- `401`: API Key 누락/무효
- `400`: 요청 형식/파라미터 검증 실패
- `500`: 내부 처리 예외

## 구현 포인트 요약
- 조직 결정권은 클라이언트 입력이 아니라 인증 결과에 있다.
- 이 원칙으로 테넌트 경계 위조(body/query 변조)를 차단한다.
- 경로 선택은 `SecurityConfig`의 `SecurityFilterChain`이 담당하고, 필터는 자기 체인 안에서 인증만 수행한다.
