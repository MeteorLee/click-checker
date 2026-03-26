# 22. JWT 관리자 대시보드 집계 API 계획 (v1.1)

## 목표
- 브라우저 콘솔이 `X-API-Key` 없이 JWT만으로 organization별 집계 결과를 안전하게 조회할 수 있는 admin analytics API를 만든다.
- 기존 analytics 계산 로직은 최대한 재사용하고, organization scope와 접근 제어만 `JWT + membership` 기준으로 다시 올린다.
- 대시보드 1차 화면에 바로 연결할 수 있는 최소 읽기 API 묶음을 먼저 닫는다.

---

# 22.0 고정 원칙

## 1. 이번 단계는 "새 분석 엔진"이 아니라 "새 읽기 진입점"을 만든다
- 기존 aggregate / trend / user / activity / funnel / retention 계산 로직을 다시 설계하지 않는다.
- 이번 단계의 핵심은 브라우저가 사용할 수 있는 JWT 기반 admin 경로를 여는 것이다.
- 즉 "기존 분석 기능 재사용 + 관리자 경로 재노출" 단계로 본다.

## 2. 브라우저는 API key를 직접 사용하지 않는다
- 브라우저 콘솔은 `Authorization: Bearer <accessToken>`만 사용한다.
- `X-API-Key`는 organization machine credential로 유지하고, 브라우저 저장소나 네트워크 요청에 직접 실어 나르지 않는다.
- 기존 `/api/v1/events/analytics/**`는 외부/서버 간 호출용으로 계속 유지한다.

## 3. organization scope는 path + membership으로 결정한다
- admin analytics API는 path의 `organizationId`를 필수로 받는다.
- 서버는 `accountId + organizationId` membership을 먼저 확인한다.
- path에 `organizationId`가 있다고 해서 그 값을 신뢰하는 것이 아니라, membership으로 접근 가능 여부를 다시 검증한다.

## 4. 인가는 현재 membership/service 구조를 그대로 사용한다
- 이번 단계에서도 권한 모델은 `OrganizationMember` 기반으로 유지한다.
- `OWNER / ADMIN / VIEWER` 중 읽기 집계는 1차에서 모두 허용하는 방향을 우선으로 본다.
- `@PreAuthorize`, `GrantedAuthority` 중심 인가 구조로 바로 옮기지 않는다.

## 5. 1차 범위는 overview 하나로 제한한다
- overview

설명:
- overview는 route template / event type mapping 같은 정규화 설정 없이도 보여줄 수 있는 가장 기본 지표다.
- routes / event-types / trends는 후속 단계로 둔다.
- 1차는 "JWT + membership 기반 overview 조회"를 가장 먼저 닫는 데 집중한다.

## 6. timezone은 1차에서 `Asia/Seoul`로 고정한다
- 1차 admin analytics API는 timezone 파라미터를 받지 않는다.
- 날짜/시계열 해석 기준은 `Asia/Seoul`로 고정한다.
- 다중 timezone 지원은 후속 과제로 둔다.

## 7. 응답 계약은 기존 analytics API와 최대한 유사하게 유지한다
- 기존 `/api/v1/events/analytics/**`의 파라미터 의미와 결과 구조를 가능한 한 재사용한다.
- 차이는 인증 경계와 organization scope 결정 방식에 둔다.
- 계산 로직 복붙보다 얇은 admin controller/service 계층을 우선한다.

## 8. 다음 단계의 화면 작업과 바로 이어질 수 있어야 한다
- 이번 단계는 대시보드 UI 작업의 선행 단계다.
- API가 만들어진 직후 organization 선택 -> 기간 선택 -> overview 조회가 바로 가능해야 한다.

---

# 22.1 범위 정의

## 포함(in scope)
- JWT 기반 admin analytics API 경로 추가
- organization membership 기반 접근 검증
- overview admin 경로 재노출
- 기존 analytics service 재사용 구조 정리
- admin analytics 응답 DTO 정의
- JWT / membership / organization scope 경계 통합 테스트

## 제외(out of scope)
- 기존 `/api/v1/events/analytics/**` 제거
- 브라우저에서 API key 직접 사용
- routes / event-types / trends / users / activity / funnels / retention admin API 구현
- 대시보드 UI 구현
- 차트/상태관리 설계
- organization lifecycle 변경
- membership / RBAC 정책 변경

---

# 22.2 완료 기준 (Done)

- 브라우저 콘솔은 JWT access token만으로 overview 데이터를 조회할 수 있다.
- admin analytics API는 `/api/v1/admin/organizations/{organizationId}/analytics/**` 아래에서 동작한다.
- organization scope는 path의 `organizationId`와 membership 검증으로 결정된다.
- membership이 없으면 `403`, organization이 없으면 `404`, JWT가 없거나 무효면 `401`이 난다.
- 같은 organization에 대해 key 기반 overview API와 jwt 기반 admin overview API의 핵심 지표가 일관된다.
- overview 경로에 대해 통합 테스트가 있다.

---

# 22.3 현재 상태

## 이미 있는 것
- `Account + JWT`
- `OrganizationMember + RBAC`
- Spring Security `SecurityContext + @AuthenticationPrincipal`
- 경로별 `SecurityFilterChain`
- 기존 API key 기반 analytics API
  - `/api/v1/events/analytics/**`

## 현재 구조의 장점
- 브라우저는 이미 JWT로 로그인할 수 있다.
- 컨트롤러는 `AdminPrincipal`을 통해 현재 로그인한 account를 표준 방식으로 읽을 수 있다.
- organization membership 기반 인가 로직도 서비스 계층에 이미 있다.
- 기존 analytics 계산 로직이 이미 제품 수준으로 존재한다.

## 현재 구조의 한계
- 브라우저가 지금 바로 쓸 수 있는 JWT 기반 analytics 읽기 경로는 아직 없다.
- 기존 analytics API는 API key organization 인증을 전제로 해서, 브라우저 대시보드에서 직접 호출하기 어렵다.
- 대시보드 화면 작업을 시작하려면 먼저 admin analytics API 경로가 필요하다.

즉 이번 단계는 "로그인 가능한 관리자 계정"과 "이미 있는 분석 로직" 사이에, 브라우저가 직접 사용할 수 있는 집계 진입점을 추가하는 단계다.

---

# 22.4 선행 확정 항목

## 인증/인가 경계
- 인증:
  - JWT access token
- organization scope:
  - path의 `organizationId`
- 인가:
  - `accountId + organizationId` membership 확인

## 1차 role 정책
- `OWNER`, `ADMIN`, `VIEWER` 모두 읽기 집계는 허용한다.
- 단, organization 설정 변경이나 member 관리와 같은 쓰기 작업 정책은 이번 단계 범위 밖이다.

## 1차 admin analytics API 목록
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`

## 재사용 원칙
- overview는 기존 overview/aggregate service를 재사용한다.
- 동일 계산을 admin 전용으로 복붙하지 않는다.

## 1차 파라미터 정책
- overview는 `from`, `to`만 받는다.
- timezone 파라미터는 받지 않는다.
- 1차 날짜/시간 해석은 `Asia/Seoul` 기준으로 고정한다.
- 복잡한 필터/정렬/breakdown 옵션은 후속 단계로 둔다.

## 정규화 설정 의존성 정책
- overview는 route template / event type mapping이 없어도 조회 가능해야 한다.
- route template / event type mapping이 있어야 의미가 생기는 집계(routes, event-types)는 후속 단계로 미룬다.
- 즉 1차 overview API는 정규화 설정 테이블 유무 때문에 차단되지 않는다.

## 응답 계약 원칙
- 기존 analytics 응답 구조를 최대한 유지한다.
- 대시보드에서 바로 쓰기 어려운 형태가 있더라도, 1차는 계산 재사용과 인증 경계 정리에 우선순위를 둔다.
- 필요한 최소 매핑만 admin 응답 DTO에서 수행한다.

---

# 22.5 설계 방향

## 1) 경로 구조
- 기본 경로:
  - `/api/v1/admin/organizations/{organizationId}/analytics/**`

설명:
- 현재 organization 선택 상태를 브라우저와 서버가 명시적으로 공유할 수 있다.
- account가 여러 organization에 속할 수 있으므로 path에 organization을 드러내는 편이 자연스럽다.

## 2) controller 구조
- admin analytics controller를 새로 둔다.
- 이 controller는 `@AuthenticationPrincipal AdminPrincipal`과 path의 `organizationId`를 받는다.
- query parameter는 `from`, `to`만 받는다.
- controller는 얇게 유지하고, membership 확인과 기존 overview service 호출은 별도 service/facade로 넘긴다.

## 3) service/facade 구조
- 역할:
  - `accountId` 추출
  - `organizationId` 검증
  - membership 접근 검증
  - 기존 overview service 호출
  - 필요 시 admin response DTO 매핑

원칙:
- 새 집계 로직을 만들지 않는다.
- admin 경로 전용 orchestration 계층만 추가한다.

## 4) membership 검증 방식
- 기본 규칙:
  - organization 없음 -> `404`
  - membership 없음 -> `403`
  - 읽기 권한 없음 -> `403`

설명:
- `organizationId`를 path로 받더라도, membership이 없으면 접근할 수 없다.
- 즉 URL에 ID를 넣는 것은 괜찮지만, 접근 권한은 항상 서버가 다시 검증한다.

## 5) 테스트 전략
- 성공 케이스:
  - membership 있는 account가 overview 정상 조회
- 실패 케이스:
  - JWT 없음 -> `401`
  - 다른 organization id -> `403`
  - organization 없음 -> `404`
- 비교 케이스:
  - 같은 organization에 대한 기존 overview API 결과와 핵심 필드 일치

---

# 22.6 실행 순서

## 1) 계획 문서 고정
- 이번 문서로 1차 범위를 `overview` 하나로 잠근다.

## 2) admin analytics 진입점 설계
- controller 경로 확정
- service/facade 책임 확정
- membership 검증 위치 확정

## 3) 1차 API 구현
1. overview

## 4) 테스트 추가
1. JWT 인증 경계
2. membership 경계
3. organization 404 경계
4. 기존 analytics API와 핵심 결과 비교

## 5) 대시보드 연결 준비
- 프론트에서 organization 선택 후 바로 붙일 수 있는 overview 호출 시나리오 정리

---

# 22.7 검증 계획

## 보안 경계 검증
- JWT 없이 admin analytics API 접근 시 `401`
- membership 없는 organization 접근 시 `403`
- 존재하지 않는 organization 접근 시 `404`

## 결과 정합성 검증
- 동일 organization / 동일 기간 조건에서
  - 기존 API key overview API 결과
  - 새 JWT admin overview API 결과
  주요 필드가 일치해야 한다.

## 회귀 범위
- `./gradlew test`
- 필요 시 admin analytics 통합 테스트 중심 선택 실행

---

# 22.8 후속 작업 연결

## 바로 다음 단계
- 대시보드 화면 1차
  - organization 선택
  - 기간 선택
  - overview 표시

## 후속 확장
- `routes`
- `event-types`
- `trends`
- `users`
- `activity`
- `funnels`
- `retention`

## 보조 backlog
- admin analytics 응답 구조 표준화 필요 여부 검토
- key 기반 analytics API와 admin analytics API의 공통 계층 정리
- 읽기 전용 membership 정책을 별도 policy/service로 더 명확히 분리할지 검토
