# 19. 관리자 대시보드 집계 API 계획 (v1.0)

## 목표
- 브라우저 콘솔 화면이 기존 `X-API-Key` 집계 API를 직접 호출하지 않고도, organization별 집계 결과를 안전하게 조회할 수 있는 admin 경로를 만든다.
- 기존 집계/분석 로직은 최대한 재사용하되, organization scope 결정 방식만 `JWT + membership` 기준으로 다시 올린다.
- 기존에 만든 집계/분석 기능을 콘솔에서도 볼 수 있게 admin 전용 읽기 API로 다시 연다.

---

# 19.0 고정 원칙

## 1. 이번 단계는 "콘솔용 읽기 API"에 집중한다
- 이번 단계의 핵심은 브라우저 콘솔이 쓸 수 있는 JWT 기반 집계 API를 만드는 것이다.
- 기존 analytics 계산 로직을 다시 설계하거나, 화면 전체를 한 번에 완성하는 단계로 보지 않는다.
- 즉 이번 단계는 "콘솔용 집계 진입점 1차" 단계다.

## 2. 브라우저는 API key를 직접 들고 다니지 않는다
- 브라우저 콘솔은 `Authorization: Bearer <accessToken>`만 사용한다.
- `X-API-Key`는 organization machine credential로 유지하고, 브라우저에 직접 노출하지 않는다.
- 따라서 콘솔용 집계는 기존 외부 분석 API와 별도 admin 경로로 분리한다.

## 3. organization scope는 path + membership으로 결정한다
- 1차 버전에서는 admin 집계 API도 path의 `organizationId`를 명시한다.
- 서버는 `accountId + organizationId` membership을 먼저 확인한다.
- 즉 기존 `authOrgId`를 API key 필터에서 받는 대신, admin 경로에서는 membership 검사 후 명시적 `organizationId`를 집계 서비스에 넘긴다.

## 4. 기존 `X-API-Key` 집계 API는 그대로 둔다
- `/api/v1/events/analytics/**`는 외부 클라이언트/SDK/서버 간 호출용으로 계속 유지한다.
- admin 집계 API가 생겨도, 기존 외부 집계 API를 바로 제거하지 않는다.
- 두 집계 API는 계산 로직을 공유할 수 있지만, 인증 경계는 분리한다.

## 5. 이번 단계는 기존 집계 기능을 콘솔 경로로 다시 연다
- 핵심 방향은 "새 분석 기능 추가"가 아니라 "기존 분석 기능의 콘솔 진입점 추가"다.
- 대시보드 화면에서 필요한 결과를 우선 노출하되, 기존 분석 API와 계산 계약은 가능한 한 그대로 유지한다.
- overview, paths, routes, eventTypes, time-buckets, users, funnels, retention을 admin 경로에서 순차적으로 다시 여는 것을 목표로 본다.

## 6. 재사용 가능한 계산 로직을 우선한다
- 가능하면 기존 analytics service / query 조합을 그대로 재사용한다.
- admin 전용 컨트롤러와 membership 검증 계층만 새로 만든다.
- 즉 "새 분석 제품"이 아니라 "새 진입점"으로 설계한다.

## 7. 관리자 화면은 집계 화면 중심으로 간다
- 이번 단계에서 조직/멤버 관리 화면은 보조로 둔다.
- 실제 데모의 중심은 집계 결과 대시보드다.
- 즉 API 설계도 대시보드에 필요한 읽기 계약을 우선순위로 삼는다.

---

# 19.1 범위 정의

## 포함(in scope)
- JWT 기반 admin 집계 API 경로 추가
- organization membership 기반 접근 검증
- 기존 집계/분석 API의 admin 경로 재노출
- 기존 analytics 로직 재사용 구조 정리
- 콘솔용 조직 선택 흐름에 맞는 path 계약 정리
- 콘솔용 대시보드 화면 설계 기준 정리
- 기존 집계 API의 구조적 문제 정리
  - `/aggregates/count` 제거 여부 확정
  - route/event type resolver 반복 조회 완화
  - 시계열 API 요청 범위 제한

## 제외(out of scope)
- 기존 `/api/v1/events/analytics/**` 제거
- 브라우저에서 API key 직접 사용
- usage / 플랜 / 429
- 대규모 프론트 상태 관리
- chart 라이브러리/화면 세부 구현 최적화
- audit log
- organization disable / delete

---

# 19.2 완료 기준 (Done)

- 브라우저 콘솔은 JWT access token만으로 최소 대시보드 데이터를 조회할 수 있다.
- 브라우저 콘솔은 JWT access token만으로 기존 집계 기능을 organization 단위로 조회할 수 있다.
- admin 집계 API는 `organizationId` path와 membership 검사로 organization scope를 결정한다.
- 기존 `X-API-Key` 기반 집계 API와 계산 결과가 핵심 지표에서 일관된다.
- 브라우저/콘솔는 API key를 직접 보관하거나 전송하지 않는다.
- `/aggregates/count`는 제거하거나 조직 스코프가 보장되는 형태로 정리된다.
- route/event type 해석에서 같은 organization 규칙을 반복 조회하지 않도록 개선된다.
- 시계열 API는 버킷 수가 과도하게 커지는 요청을 제한할 수 있다.
- 대시보드 화면에서 organization 선택 후 주요 집계 결과를 확인할 수 있다.

---

# 19.3 현재 상태

## 이미 있는 것
- `signup / login / refresh / logout / me`
- `OrganizationMember` 기반 `OWNER / ADMIN / VIEWER`
- `admin organization create + API key issue/rotate`
- 기존 `X-API-Key` 기반 analytics API
  - `/api/v1/events/analytics/**`

## 현재 구조의 한계
- 브라우저 콘솔이 지금 바로 쓸 수 있는 JWT 기반 집계 API가 없다.
- 기존 집계 API는 `X-API-Key -> authOrgId`를 기대하므로, 콘솔 화면에서 직접 호출하기 어렵다.
- 브라우저에 API key를 넘기면 보안상/설계상 경계가 흐려진다.
- 일부 집계 API는 콘솔로 그대로 올리기 전에 구조 정리가 필요하다.
  - `/aggregates/count`는 현재 organization scope를 보장하지 못한다.
  - route/event type resolver가 집계 중 반복 조회되어 요청당 쿼리 수가 커질 수 있다.
  - 시계열 API는 요청 범위 제한이 없어 큰 구간에서 응답이 쉽게 커질 수 있다.
- 따라서 "사람 계정 기반 콘솔"과 "조직 machine credential 기반 외부 집계 API" 사이에 한 층이 더 필요하다.

즉 이번 단계는 "기존 집계 계산을 다시 만드는 단계"가 아니라, "JWT 기반 콘솔 진입점을 추가하는 단계"다.

---

# 19.4 선행 확정 항목

## 인증 경계
- 콘솔은 JWT만 사용한다.
- 브라우저는 `X-API-Key`를 직접 사용하지 않는다.
- 외부 집계 API와 콘솔 집계 API는 인증 경계를 분리 유지한다.

## organization scope 결정 방식
- admin 집계 API는 `organizationId` path를 필수로 받는다.
- membership이 없으면 `403`
- role 부족이면 `403`
- organization 자체가 없으면 `404`

## 최소 role 정책
- 1차 버전에서는 `OWNER`, `ADMIN`, `VIEWER` 모두 읽기 집계는 허용 가능하다.
- 단, 화면/설정 수정은 이 단계에서 다루지 않는다.

## 1차 admin 집계 API 후보
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/raw-event-types`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/event-types`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/event-types/unique-users`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/paths`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/routes`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/routes/unmatched-paths`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/routes/unique-users`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/route-event-types`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/time-buckets`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/route-time-buckets`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/event-type-time-buckets`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/route-event-type-time-buckets`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/users/overview`
- `POST /api/v1/admin/organizations/{organizationId}/analytics/funnels/report`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/retention/daily`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/retention/matrix`

## 기존 집계 로직 재사용 원칙
- 가능한 한 기존 aggregate analytics service를 재사용한다.
- admin API는 membership 검증과 response shaping이 추가된 얇은 계층으로 둔다.
- 동일 계산 로직을 복붙하지 않는다.

## 이번 단계에서 함께 정리할 기존 집계 API 이슈
- `GET /api/v1/events/analytics/aggregates/count`
  - 개발용 성격이 강하고 현재 organization scope가 깨져 있다.
  - 콘솔 경로로 옮기기보다 제거를 우선 검토한다.
- route / canonical event type resolver
  - 집계 중 같은 organization 규칙을 반복 조회하지 않도록, 요청 단위 preload 또는 캐시 구조를 검토한다.
- time-buckets 계열
  - `from`, `to`, `bucket` 조합으로 버킷 수가 과도하게 늘어나는 요청을 제한하는 정책을 추가한다.

---

# 19.5 API 설계

## 기본 경로 원칙
- admin 집계 API는 `/api/v1/admin/organizations/{organizationId}/analytics/**` 아래에 둔다.
- 기존 `/api/v1/events/analytics/**`와 응답 구조를 최대한 비슷하게 유지한다.
- 차이는 인증/스코프 경계만 둔다.

---

# 19.6 화면 설계 기준

## 1) 최소 화면 구성
- 로그인/회원가입
- organization 선택 + 대시보드
- 조직/멤버 관리(보조)

## 2) 대시보드 우선순위
- overview
- paths / routes
- event-types
- time-buckets
- 이후 users / funnels / retention을 순서대로 연결한다.

## 3) 멤버 관리 화면 비중
- 존재는 보여주되, 메인 화면으로 키우지 않는다.
- 이번 포트폴리오의 주인공은 집계 결과 화면이다.

---

# 19.7 보안 / 운영 설계

## 브라우저 보안 원칙
- 브라우저 local state는 access token 기준으로만 운영한다.
- API key는 브라우저 저장소에 보관하지 않는다.
- 콘솔 화면은 admin 집계 API만 호출한다.

## 테스트 관점
- 동일 organization에 대해
  - `X-API-Key` 집계 API 결과
  - JWT admin 집계 API 결과
  핵심 지표가 맞는지 비교할 수 있어야 한다.
- 제거 후보인 `/aggregates/count`는 별도 회귀선을 만들기보다, 제거 여부를 먼저 확정한다.
- route/event type resolver preload와 시계열 범위 제한은 성능/안정성 회귀 테스트 기준을 남긴다.

## 배포 / 데모 관점
- 데모 시나리오는 아래처럼 이어진다.
  - signup
  - organization 생성
  - API key 수령
  - sample ingest
  - login
  - admin dashboard 조회

---

# 19.8 실행 순서

## 1) 기존 집계 API 구조 정리
1. `/aggregates/count` 제거 여부 확정
2. route/event type resolver 반복 조회 완화
3. time-buckets 계열 범위 제한 추가

## 2) membership 검증 연결
1. `organizationId` path 기준 검증
2. 읽기 role 허용 범위 확정

## 3) admin 집계 API 재노출
1. overview / paths / routes / event-types / time-buckets
2. users / funnels / retention

## 4) 검증
1. 같은 organization에 대한 key 기반 / jwt 기반 결과 비교
2. membership 없는 계정 차단 확인
3. role이 있는 계정의 대시보드 조회 확인

## 5) 대시보드 화면 연결
1. organization 선택
2. 기간 선택
3. overview + 분포 + 심화 분석 화면

---

# 19.9 체크포인트

## 보안
- 브라우저에 API key가 노출되지 않는지
- admin 집계 API가 membership 없이 열리지 않는지

## 제품 메시지
- 집계 결과 화면이 포트폴리오의 메인 화면으로 충분히 설명 가능한지
- 멤버 관리보다 분석 화면에 힘이 실리는지

## 재사용
- 기존 analytics 계산 로직 복붙 없이 재사용하는지
- key 기반 집계와 jwt 기반 집계 결과가 불필요하게 갈라지지 않는지

---

# 19.10 후속 작업
- funnel / retention / users overview의 admin 경로 확장
- organization switch UI 고도화
- 프론트 상태 관리 개선
- chart/interaction 고도화
- admin 집계 API의 SecurityContext 정식화
