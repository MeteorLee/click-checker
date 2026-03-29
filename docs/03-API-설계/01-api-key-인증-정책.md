# API Key 인증 정책 (현재 기준)

## 목적
- 이벤트 수집/조회 API의 조직 스코프를 인증으로 강제한다.
- 요청 payload/query의 `organizationId`를 신뢰하지 않는다.

## 적용 범위 (현재)
- 보호 대상: `/api/events/**`, `/api/v1/events/**`
- 비보호 대상(현재): `/api/organizations/**`
- 참고:
  - `POST /api/organizations`는 기존 공개 organization 생성 경로다.
  - 현재 포트폴리오 기준 주 경로는 `POST /api/v1/admin/organizations`이며, 공개 경로는 레거시로 취급한다.
  - 관리자 콘솔 경로(`/api/v1/admin/**`)는 API Key가 아니라 JWT 전용 SecurityFilterChain에서 처리한다.

## 인증 방식
- 헤더: `X-API-Key`
- 검증 흐름:
  1. 헤더 누락/blank 확인
  2. 키 포맷에서 `kid` 추출
  3. `kid + ACTIVE`로 조직 후보 조회
  4. 요청 키 해시와 저장 해시를 constant-time compare로 검증
  5. 성공 시 organization 주체를 `SecurityContext`에 저장
  6. 보호 경로는 API Key 전용 `SecurityFilterChain`에서 처리한다

## 인증 컨텍스트 전달 정책
- API Key 인증 성공 후 컨트롤러는 `@AuthenticationPrincipal ApiKeyPrincipal`로 organization 정보를 받는다.
- 인증 주체 전달을 위해 `request attribute`나 custom resolver를 사용하지 않는다.
- 클라이언트 입력의 `organizationId`는 참고하지 않고, 인증 결과의 `organizationId`를 조직 스코프로 사용한다.

## 응답 정책
- 키 누락/무효: `401 Unauthorized`
- 2단계 MVP에서는 `401` 응답 바디를 고정하지 않는다(후속 정리)

## 데이터 저장 정책
- API Key 평문은 DB에 저장하지 않는다.
- 저장 필드: `apiKeyKid`, `apiKeyHash`, `apiKeyPrefix`, `apiKeyStatus`
- 평문 키는 조직 생성/재발급 응답에서 1회만 반환한다.

## 보안/로그 정책
- 로그에 API Key 원문을 남기지 않는다.
- 키 관련 로그 식별자는 `kid` 또는 `prefix`만 허용한다.
- API Key 필터는 Spring Security 체인 안에서만 동작하고, 일반 서블릿 필터로 중복 등록하지 않는다.

## 현재 제한/후속 과제
- `organizations`의 API Key 컬럼 `NOT NULL` 강화는 3단계(Flyway)에서 백필 후 적용
- API Key 인증 로그 하드닝(실패 사유 코드, 원문 미노출 검증) 추가 예정
- 보호 범위 `/api/**` 확장은 후속 단계에서 진행
