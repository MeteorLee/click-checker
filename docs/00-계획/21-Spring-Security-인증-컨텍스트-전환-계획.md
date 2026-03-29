# 21. Spring Security 인증 컨텍스트 전환 계획 (v1.0)

## 목표
- 현재 `request attribute + resolver` 기반 인증 주체 전달 방식을 Spring Security `SecurityContext` 기반으로 정리한다.
- 기존 `ApiKeyAuthFilter`, `JwtAuthFilter`는 유지하되, 인증 성공 결과를 Spring Security 표준 방식으로 저장한다.
- 조직 멤버십 기반 인가(`OrganizationMember`, `OWNER / ADMIN / VIEWER`)는 그대로 유지하고, 이후 JWT 기반 admin analytics API와 대시보드 작업의 기반을 만든다.

---

# 21.0 고정 원칙

## 1. 이번 단계는 "인증 전달 방식 정리"에 집중한다
- 이번 단계의 핵심은 인증 정보를 어디에 어떻게 저장하고 꺼내 쓰는지 정리하는 것이다.
- API Key 검증 규칙, JWT 검증 규칙, 조직 멤버십 기반 인가 정책을 한 번에 바꾸지 않는다.
- 즉 이번 단계는 "보안 구조 전면 재설계"가 아니라 "인증 전달 표준화" 단계다.

## 2. 필터는 유지하고 역할만 줄인다
- `ApiKeyAuthFilter`, `JwtAuthFilter`는 계속 사용한다.
- 두 필터는 여전히 헤더 파싱, 토큰/API Key 검증, 계정/조직 조회, 실패 응답 처리까지 맡는다.
- 다만 인증 성공 후 결과 저장을 `request attribute`가 아니라 `SecurityContext`로 옮긴다.

## 3. 인증과 인가는 계속 분리한다
- 인증은 "누구인가 / 어떤 조직 키인가"를 식별하는 단계다.
- 인가는 "이 account가 이 organization에서 무엇을 할 수 있는가"를 판단하는 단계다.
- 이번 단계에서는 인증만 Spring Security 표준화하고, 인가는 기존 membership 기반 서비스 로직을 유지한다.

## 4. 조직 단위 인가 모델은 그대로 둔다
- `OrganizationMember` 기반 role 모델(`OWNER / ADMIN / VIEWER`)은 이번 단계에서 바꾸지 않는다.
- `@PreAuthorize`, `PermissionEvaluator`, `GrantedAuthority` 중심의 정식 Spring Security 인가 구조로 바로 옮기지 않는다.
- 이유는 현재 프로젝트의 권한 모델이 "전역 사용자 role"보다 "조직 membership role"에 더 가깝기 때문이다.

## 5. API Key와 JWT는 여전히 서로 다른 인증 수단으로 취급한다
- API Key는 organization machine credential이다.
- JWT는 로그인한 account credential이다.
- 두 인증 주체는 같은 principal 의미로 합치지 않고, 별도 principal 타입으로 유지한다.

## 6. resolver 제거는 단계적으로 진행한다
- `@CurrentOrganizationId`, `@CurrentAccountId`와 resolver는 이번 단계에서 최종적으로 제거 대상이다.
- 다만 전환 과정에서는 호환성 유지용으로 잠시 공존할 수 있다.
- 즉 "필터 전환 -> 컨트롤러 전환 -> resolver 제거" 순서를 고정한다.

## 7. 이후 admin analytics API를 위한 선행 정리로 본다
- 이번 단계는 그 자체로 끝나는 작업이 아니라, 이후 `JWT + membership` 기반 admin analytics API의 기반 정리 단계다.
- 대시보드용 API는 브라우저가 JWT만 사용하도록 만들 예정이므로, 현재 인증 전달 구조를 먼저 정리하는 것이 우선이다.

---

# 21.1 범위 정의

## 포함(in scope)
- Spring Security 인증 주체 전달 방식 정리
- API Key용 principal 설계
- JWT용 principal 설계
- 필터 성공 처리의 `SecurityContext` 전환
- `SecurityConfig`의 보호/비보호 경로 명시 정리
- controller의 인증 주체 접근 방식 전환
- 기존 resolver 제거 계획 수립 및 적용
- 필터 / 보안 경계 회귀 테스트 보강

## 제외(out of scope)
- 조직 멤버십 / RBAC 정책 변경
- `OWNER / ADMIN / VIEWER` 권한 모델 재설계
- `@PreAuthorize` 기반 권한 체계 전면 도입
- admin analytics API 구현
- 대시보드 UI 구현
- API Key 다중 발급 모델 도입
- 브라우저 토큰 저장 전략 변경

---

# 21.2 완료 기준 (Done)

- API Key 인증 성공 시 organization 주체 정보가 `SecurityContext`에 저장된다.
- JWT 인증 성공 시 account 주체 정보가 `SecurityContext`에 저장된다.
- `SecurityConfig`에서 공개/보호 경로가 현재 구조 기준으로 명확히 선언된다.
- controller는 최소 한 묶음 이상에서 resolver 대신 Spring Security 방식으로 인증 주체를 주입받는다.
- 기존 membership 기반 인가 로직이 회귀 없이 유지된다.
- `request attribute + resolver` 기반 인증 전달 코드는 제거되거나, 제거 직전의 최소 호환 상태로 축소된다.
- 이후 admin analytics API가 `JWT + organizationId path + membership` 구조로 자연스럽게 이어질 수 있다.

---

# 21.3 현재 상태

## 이미 있는 것
- `ApiKeyAuthFilter`
- `JwtAuthFilter`
- `SecurityConfig`
- `@CurrentOrganizationId`, `CurrentOrganizationIdResolver`
- `@CurrentAccountId`, `CurrentAccountIdResolver`
- `Account + JWT`
- `OrganizationMember + RBAC`

## 현재 구조의 장점
- 필터 단계에서 API Key/JWT 검증은 이미 동작하고 있다.
- 인증 실패 응답(`401`, `403`) 경계도 현재 기준으로 정리돼 있다.
- membership 기반 인가 로직도 서비스 계층에서 동작 중이다.

## 현재 구조의 한계
- 인증 성공 정보를 `SecurityContext`가 아니라 `request attribute`에 저장한다.
- controller는 Spring Security 표준 주입 대신 custom resolver에 의존한다.
- `SecurityConfig`가 실제 보호 정책의 중심이라기보다 필터 등록용에 가깝다.
- 이후 admin analytics API나 대시보드 경로를 확장할수록 현재 전달 방식이 중복 구조가 된다.

즉 이번 단계는 "인증은 이미 되는데, 전달 방식이 임시 구조에 머물러 있는 상태"를 정리하는 단계다.

---

# 21.4 선행 확정 항목

## 인증 principal 분리
- API Key 인증 principal은 organization 주체로 본다.
- JWT 인증 principal은 account 주체로 본다.
- 두 principal을 하나의 통합 user 모델로 억지로 합치지 않는다.

## API Key principal 최소 정보
- `organizationId`
- 필요 시 `apiKeyKid`

설명:
- 현재 API Key는 `Organization`에 귀속된 단일 키 모델이다.
- 따라서 API Key principal은 사실상 "어느 organization인가"를 전달하는 얇은 객체면 충분하다.

## JWT principal 최소 정보
- `accountId`
- 필요 시 `loginId`

설명:
- 1차 목적은 "현재 로그인한 account 식별"이다.
- membership / role 정보는 principal에 싣지 않고 기존 서비스 로직에서 확인한다.

## 인가 모델 유지
- 조직 role 판단은 계속 `OrganizationMember` 기반으로 수행한다.
- `GrantedAuthority`에 `OWNER / ADMIN / VIEWER`를 즉시 매핑하지 않는다.
- 이유는 현재 권한이 전역 role이 아니라 organization별 membership role이기 때문이다.

## SecurityConfig 역할 확대
- 현재 단계부터 `SecurityConfig`는 단순 필터 등록이 아니라 보호 정책 선언 지점이 되어야 한다.
- 공개 경로와 보호 경로를 코드에서 명시적으로 읽을 수 있어야 한다.

## 공개/보호 경로 기준
- 공개 경로
  - `/api/v1/admin/auth/signup`
  - `/api/v1/admin/auth/login`
  - `/api/v1/admin/auth/refresh`
  - `/api/v1/admin/auth/logout`
  - `/actuator/health`
  - 필요 시 `/error`
- 보호 경로
  - `/api/v1/admin/**`
  - `/api/events/**`
  - `/api/v1/events/**`

설명:
- 인증 수단 분기는 현재처럼 경로 기준을 유지한다.
- 단, 실제 접근 정책은 `permitAll` 중심이 아니라 명시적 보호 쪽으로 옮긴다.

---

# 21.5 설계 방향

## 1) principal 설계

### API Key principal
- 역할:
  - 인증된 organization 주체 표현
- 최소 필드:
  - `organizationId`
  - `apiKeyKid`(선택)

### JWT principal
- 역할:
  - 인증된 account 주체 표현
- 최소 필드:
  - `accountId`
  - `loginId`(선택)

원칙:
- principal은 "현재 인증 주체를 읽기 위한 최소 정보"만 가진다.
- membership / role / organization 목록 같은 인가 정보는 넣지 않는다.

## 2) Authentication 저장 방식
- 1차 버전에서는 복잡한 커스텀 token 타입보다 Spring Security 기본 `Authentication` 구현 재사용을 우선한다.
- 즉 principal만 커스텀으로 두고, 필터에서는 인증 완료된 `Authentication` 객체를 만들어 `SecurityContext`에 넣는다.

원칙:
- 인증 성공 후 `SecurityContextHolder.getContext().setAuthentication(...)`
- 인증 실패 시 현재 필터 응답 규칙 유지

## 3) 필터 역할

### ApiKeyAuthFilter
- 유지:
  - `X-API-Key` 읽기
  - 형식 검증
  - kid 추출
  - organization 조회
  - hash 비교
  - `apiKeyLastUsedAt` 갱신
  - 실패 시 `401`
- 변경:
  - 성공 후 `request attribute` 저장 제거
  - `SecurityContext`에 organization principal 저장

### JwtAuthFilter
- 유지:
  - `Authorization: Bearer ...` 파싱
  - access token 검증
  - account 조회
  - disabled 계정 차단
  - 실패 시 `401/403`
- 변경:
  - 성공 후 `request attribute` 저장 제거
  - `SecurityContext`에 account principal 저장

## 4) controller 접근 방식
- 현재:
  - `@CurrentOrganizationId`
  - `@CurrentAccountId`
- 전환 후:
  - `Authentication`
  - 또는 `@AuthenticationPrincipal`

원칙:
- 새 code path부터 Spring Security 방식으로 전환한다.
- 기존 controller는 단계적으로 옮긴다.

## 5) resolver 정리
- 전환 초기:
  - 호환성 유지 가능
- 전환 중기:
  - controller를 Spring Security 방식으로 순차 변경
- 전환 종료:
  - `CurrentOrganizationIdResolver`
  - `CurrentAccountIdResolver`
  - 관련 annotation 제거

즉 resolver는 "즉시 삭제"가 아니라 "대체 수단 안착 후 제거" 대상으로 본다.

---

# 21.6 실행 순서

## 1) principal 구조 추가
- API Key principal 정의
- JWT principal 정의

## 2) 필터 성공 처리 전환
- `ApiKeyAuthFilter` -> `SecurityContext`
- `JwtAuthFilter` -> `SecurityContext`

## 3) SecurityConfig 정리
- 공개 경로 `permitAll`
- 보호 경로 명시
- 현재 필터 체인 기준과 충돌 없는지 확인

## 4) controller 전환
- admin controller부터 Spring Security 방식으로 전환
- 이후 ingest / analytics controller를 순차 전환

## 5) resolver 제거
- 남은 사용처 제거
- resolver / annotation / 관련 설정 정리

## 6) 테스트 정리
- 필터 테스트
- 보안 경계 테스트
- membership 기반 인가 회귀 테스트

---

# 21.7 검증 계획

## 필터 단위 검증
- API Key 성공 시 `SecurityContext`에 organization principal 존재
- JWT 성공 시 `SecurityContext`에 account principal 존재
- 실패 시 기존처럼 `401/403`

## 보안 경계 검증
- 공개 경로는 인증 없이 접근 가능
- admin 보호 경로는 JWT 없으면 차단
- event / analytics 보호 경로는 API Key 없으면 차단

## 인가 회귀 검증
- membership 없음 -> `403`
- role 부족 -> `403`
- 마지막 owner 보호 규칙 유지

## 회귀 범위
- `./gradlew test`
- `./gradlew postgresTest`

---

# 21.8 후속 작업 연결

## 다음 단계와의 연결
- 이번 단계가 끝나면, 이후 admin analytics API는 아래 구조로 자연스럽게 이어진다.
  - 인증: JWT
  - organization scope: path의 `organizationId`
  - 인가: `OrganizationMember` membership / role 확인
  - 계산 로직: 기존 analytics service 재사용

즉 이번 단계는 대시보드용 API 구현 전 "인증 전달 구조를 정식화하는 선행 단계"로 본다.
