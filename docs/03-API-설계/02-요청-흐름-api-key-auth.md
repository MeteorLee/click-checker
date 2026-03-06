# 요청 흐름: API Key 인증 (2단계)

## 대상 요청
- `POST /api/events`
- `GET /api/events/aggregates/*`

## 처리 순서
1. `ApiKeyAuthFilter`
   - `X-API-Key` 헤더를 검증한다.
   - 누락/무효 시 `401` 반환 후 종료한다.
   - 성공 시 `AUTH_ORG_ID`를 request attribute에 저장한다.

2. `CurrentOrganizationIdResolver`
   - 컨트롤러 파라미터 `@CurrentOrganizationId`에 `AUTH_ORG_ID`를 주입한다.
   - 값이 없으면 `401`을 반환한다.

3. Controller
   - `@CurrentOrganizationId Long authOrgId`를 받는다.
   - 요청 payload/query의 `organizationId`를 사용하지 않는다.
   - 서비스에 `authOrgId`를 전달한다.

4. Service
   - `authOrgId`로 Organization을 조회한다.
   - 이벤트 저장/조회를 organization 스코프로 수행한다.

## EventUser 연결 규칙 (수집 시)
- `externalUserId`가 있으면 `(authOrgId, externalUserId)`로 조회한다.
- 없으면 새 `EventUser`를 생성해 연결한다.
- `externalUserId`가 없으면 익명 이벤트(`eventUser=null`)로 저장한다.

## 실패 응답 요약
- `401`: API Key 누락/무효 또는 인증 컨텍스트 누락
- `400`: 요청 형식/파라미터 검증 실패
- `500`: 내부 처리 예외

## 구현 포인트 요약
- 조직 결정권은 클라이언트 입력이 아니라 인증 결과에 있다.
- 이 원칙으로 테넌트 경계 위조(body/query 변조)를 차단한다.
