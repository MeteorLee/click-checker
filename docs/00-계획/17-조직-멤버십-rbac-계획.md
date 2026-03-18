# 17. 조직 멤버십 / RBAC 계획 (v1.2)

## 목표
- 16단계에서 만든 `Account + JWT` 인증 위에, 계정과 조직의 실제 관계를 올린다.
- `누가 로그인했는가` 다음으로 `어느 조직에 속해 있고 무엇을 할 수 있는가`를 설명 가능한 구조로 만든다.
- 이번 단계에서는 `OrganizationMember`와 최소 role 모델(`OWNER / ADMIN / VIEWER`)을 도입하고, 일부 관리자 API에 role 기반 인가를 적용한다.

---

# 17.0 고정 원칙

## 1. 이번 단계는 "조직 멤버십 + 최소 인가"에 집중한다
- 16단계에서 계정 인증 뼈대는 이미 닫혔다고 본다.
- 이번 단계의 목적은 계정과 조직의 관계, 그리고 조직 단위 role 기반 인가를 올리는 것이다.
- 플랜, usage, rate limit, 감사 로그까지 한 번에 확장하지 않는다.

## 2. 인증과 인가는 계속 분리해서 본다
- JWT는 여전히 "어떤 account인가"를 식별하는 역할만 맡는다.
- 조직 멤버십과 role은 별도의 `OrganizationMember`로 관리한다.
- 즉 17단계는 JWT를 바꾸는 단계가 아니라, JWT 위에 조직 인가를 얹는 단계다.

## 3. 조직 컨텍스트는 path 기준으로 명시한다
- 1차 버전에서는 "현재 선택된 조직" 상태를 토큰에 넣지 않는다.
- 조직 단위 관리자 API는 `organizationId`를 path에 명시한다.
- 서버는 `accountId + organizationId`로 membership과 role을 확인한다.

예:
- `GET /api/v1/admin/organizations/{organizationId}/members`
- `PUT /api/v1/admin/organizations/{organizationId}/members/{memberId}/role`

## 4. role 모델은 최소로 시작한다
- 이번 단계는 `OWNER / ADMIN / VIEWER` 3단계로 고정한다.
- 세부 권한을 너무 잘게 쪼개지 않는다.
- 제품 초기 단계에서 설명 가능한 권한 체계를 우선한다.

## 5. 한 account는 여러 organization에 속할 수 있다
- `Account`와 `Organization`은 직접 1:N으로 묶지 않는다.
- `OrganizationMember`를 통해 다대다 관계를 표현한다.
- 이 전제를 먼저 고정해야 이후 조직 전환, 멤버 관리, 콘솔 구조가 자연스럽다.

## 6. 마지막 OWNER 보호 규칙을 둔다
- 어떤 organization에도 최소 1명의 `OWNER`는 남아 있어야 한다.
- 마지막 owner를 제거하거나 role을 강등하는 동작은 허용하지 않는다.

## 7. 인가 적용은 "현재 구조 위에 얹는 방식"으로 시작한다
- 1차 버전에서는 현재 `JwtAuthFilter + @CurrentAccountId` 구조를 유지한다.
- 즉 현재 단계에서는 `SecurityContext` 기반 principal 주입으로 바로 옮기지 않는다.
- membership / role 검사는 서비스 또는 별도 인가 helper에서 수행한다.
- `SecurityContext`, `@PreAuthorize`, `PermissionEvaluator` 같은 정식 Spring Security 인가 구조는 후속 정리 대상으로 둔다.

## 8. 기존 API Key 경로는 그대로 둔다
- `/api/events/**`, `/api/v1/events/**`의 API Key 인증/스코프 구조는 이번 단계에서 바꾸지 않는다.
- 관리자 계정 인가는 `/api/v1/admin/**` 계열에서만 먼저 확장한다.

---

# 17.1 범위 정의

## 포함(in scope)
- 계정 회원가입 API
- 조직 생성 API
- `OrganizationMember` 도메인 도입
- `OrganizationRole` 도입
- account와 organization의 다대다 관계 정리
- `/api/v1/admin/me`에 membership 정보 확장
- 조직 멤버 목록 조회 API
- 조직 멤버 추가 API(1차 방식)
- 조직 멤버 role 변경 API
- 조직 멤버 제거 API
- 일부 관리자 API에 role 기반 인가 적용
- 마지막 owner 보호 규칙 적용

## 제외(out of scope)
- 플랜 / usage / 429 정책
- Audit Log
- 이메일 초대 / 이메일 인증
- 비밀번호 찾기 / 비밀번호 재설정
- 관리자 콘솔 UI
- organization 즉시 삭제 기능
- 세밀한 permission matrix
- Spring Security principal / PermissionEvaluator 정식화

참고:
- organization lifecycle은 1차에서 즉시 삭제보다 비활성화 중심으로 후속 설계한다.
- 데이터 보존과 운영 안전성을 고려해, 삭제는 후순위 관리 기능으로 둔다.

---

# 17.2 완료 기준 (Done)

- 한 account가 하나 이상의 organization에 membership으로 연결될 수 있다.
- 회원가입은 `Account`만 생성하고, 조직 role은 만들지 않는다.
- organization 생성 시 creator account가 해당 organization의 `OWNER` membership으로 자동 연결된다.
- `/api/v1/admin/me`에서 현재 account의 organization membership 목록을 확인할 수 있다.
- `OWNER / ADMIN / VIEWER` 역할이 코드와 문서에 반영된다.
- 조직 멤버 목록 조회 / 추가 / role 변경 / 제거가 최소 1차 버전으로 동작한다.
- role에 따라 허용/차단되는 API가 최소 한 묶음 이상 실제로 구분된다.
- 마지막 owner 보호 규칙이 실제 코드에서 차단된다.
- 기존 API Key 인증 경로와 새 membership/RBAC 경로가 충돌하지 않는다.

---

# 17.3 현재 상태

## 이미 있는 것
- `Account` 기반 로그인 구조
- JWT access token / refresh token
- `login / refresh / logout / me`
- `/api/v1/admin/**` 최소 보호 경로
- `X-API-Key` 기반 organization 인증 경계

## 현재 구조의 한계
- 로그인은 되지만, 어떤 account가 어느 organization에 속하는지 표현할 방법이 없다.
- 따라서 "이 계정이 이 조직 설정을 수정할 수 있는가"를 설명할 수 없다.
- 현재 `/api/v1/admin/me`는 account 정보만 보여줄 뿐, membership과 role은 없다.
- 현재는 회원가입이 없고, organization 생성 시 creator account를 owner로 연결하는 흐름도 없다.
- 이후 관리자 콘솔이나 조직별 설정 관리도 membership 없이는 확장하기 어렵다.

즉 이번 단계는 "로그인 가능한 계정"을 "조직에 속한 사용자"로 확장하는 단계다.

---

# 17.4 선행 확정 항목

## account와 organization의 관계
- 한 account는 여러 organization에 속할 수 있다.
- 한 organization은 여러 account를 가질 수 있다.
- 따라서 관계는 `OrganizationMember`로 푼다.
- `(account_id, organization_id)`는 unique해야 한다.

## membership 최소 모델
- 1차 버전에서는 membership status를 별도 enum으로 두지 않는다.
- membership row가 존재하면 활성 membership으로 본다.
- 조직에서 제거되면 membership row를 삭제한다.
- invite / pending / suspended 같은 상태는 후속 단계로 넘긴다.

## role 모델
- `OWNER`
  - 조직 멤버 관리 가능
  - 역할 변경 가능
  - 마지막 owner 보호 규칙 적용 대상
  - 조직 핵심 설정 변경 가능
- `ADMIN`
  - 멤버 목록 조회 가능
  - 조직 설정 조회 및 일부 수정 가능
  - 멤버 추가/변경/제거는 불가
- `VIEWER`
  - 읽기 전용
  - 설정 변경과 멤버 변경은 불가

## 조직 컨텍스트 전달 방식
- 1차 버전에서는 "현재 조직 선택" 세션 상태를 두지 않는다.
- 조직 단위 관리자 API는 path의 `organizationId`로 스코프를 받는다.
- 서버는 `accountId + organizationId` membership을 직접 확인한다.

## 회원가입과 organization 생성의 경계
- 회원가입은 `Account` 생성만 담당한다.
- 회원가입 시점에는 organization role이나 membership을 만들지 않는다.
- organization은 별도 API에서 생성한다.
- organization 생성 시 현재 로그인 account를 creator로 보고, 해당 organization의 첫 `OWNER` membership을 자동 생성한다.
- 즉 첫 owner 권한은 "회원가입"이 아니라 "organization 생성" 시점에 생긴다.

## member 추가 방식
- 1차 버전에서는 이메일 초대/가입 플로우를 만들지 않는다.
- `POST /members`는 기존 `Account`를 현재 organization에 연결하는 membership 생성 API로 본다.
- 즉 `POST /members`는 새 account를 생성하지 않는다.
- 새 account 생성은 회원가입 또는 별도 account 생성 흐름에서만 수행한다.
- 이후 invite 기반 모델로 확장할 수 있다.

## 에러 정책
- 인증 실패: `401`
- organization 자체가 없음: `404`
- membership 없음: `403`
- role 부족: `403`
- `memberId`가 해당 organization 소속 membership이 아님: `404`
- 마지막 owner 제거/강등 시도: `409`
- 멤버 추가/role 변경 요청 형식 오류: `400`

---

# 17.5 도메인 설계

## 1) OrganizationMember
- 역할:
  - `Account`와 `Organization`의 연결
  - 조직 단위 role 보관
- 최소 필드:
  - `id`
  - `account`
  - `organization`
  - `role`
  - `createdAt`
  - `updatedAt`
- 제약:
  - `(account, organization)` unique
  - API의 `memberId`는 `OrganizationMember.id`를 의미한다

## 2) OrganizationRole
- 최소 값:
  - `OWNER`
  - `ADMIN`
  - `VIEWER`

## 3) Account와의 관계
- `Account`는 계속 독립 로그인 주체다.
- 조직 권한은 `Account`가 아니라 `OrganizationMember`가 가진다.
- 따라서 account disable과 membership 삭제/부재는 다른 개념으로 본다.

## 4) Organization과의 관계
- `Organization`이 membership 컬렉션을 꼭 직접 들고 있을 필요는 없다.
- 현재 프로젝트 스타일상 repository 조회 중심으로 유지해도 충분하다.

---

# 17.6 API 설계

## 1) 내 정보 확장
- `GET /api/v1/admin/me`
- 현재 응답에 아래 정보를 추가한다.
  - `memberships[]`
  - `memberships[].organizationId`
  - `memberships[].organizationName`
  - `memberships[].role`
- 정렬:
  - `organizationName asc`
  - 같으면 `membershipId asc`

예시:
```json
{
  "accountId": 1,
  "loginId": "alice",
  "status": "ACTIVE",
  "memberships": [
    {
      "membershipId": 100,
      "organizationId": 10,
      "organizationName": "Acme",
      "role": "OWNER"
    }
  ]
}
```

## 2) 회원가입
- `POST /api/v1/admin/auth/signup`
- 입력:
  - `loginId`
  - `password`
- 동작:
  - 새 `Account` 생성
- 제약:
  - organization이나 role은 생성하지 않는다

## 3) organization 생성
- `POST /api/v1/admin/organizations`
- 권한:
  - 로그인된 account
- 동작:
  - 새 `Organization` 생성
  - 현재 로그인 account를 해당 organization의 첫 `OWNER` membership으로 연결

## 4) 멤버 목록 조회
- `GET /api/v1/admin/organizations/{organizationId}/members`
- 권한:
  - `OWNER`
  - `ADMIN`
- 목적:
  - 현재 organization 멤버와 role 확인
- 식별자:
  - 응답의 `memberId`는 `OrganizationMember.id`

## 5) 멤버 추가
- `POST /api/v1/admin/organizations/{organizationId}/members`
- 입력:
  - `accountId`
  - `role`
- 최소 버전 동작:
  - 기존 `Account`를 현재 organization의 `OrganizationMember`로 연결
- 성격:
  - 새 account 생성 API가 아니다
  - 초대 수락 플로우가 아니다
  - `OWNER`가 이미 존재하는 account를 현재 organization 멤버로 등록하는 1차 관리자용 API다
- 권한:
  - `OWNER`

## 6) 멤버 role 변경
- `PUT /api/v1/admin/organizations/{organizationId}/members/{memberId}/role`
- 대상:
  - `memberId = OrganizationMember.id`
- 입력:
  - `role`
- 권한:
  - `OWNER`
- 제약:
  - 마지막 `OWNER`를 `ADMIN/VIEWER`로 강등할 수 없다

## 7) 멤버 제거
- `DELETE /api/v1/admin/organizations/{organizationId}/members/{memberId}`
- 대상:
  - `memberId = OrganizationMember.id`
- 권한:
  - `OWNER`
- 제약:
  - 마지막 `OWNER`는 제거할 수 없다

## 8) 이후 role 적용 대상
- 이번 단계 문서에서는 아래 경로를 role 적용 대상 후보로 본다.
  - 멤버 관리 API
  - API Key rotate API
  - route template / event type mapping의 admin 경로
- 실제 구현은 멤버 관리 API부터 시작하고, 나머지는 1차 또는 후속 커밋으로 분리할 수 있다.

---

# 17.7 인가 설계

## 기본 흐름
1. `JwtAuthFilter`가 accountId를 식별한다.
2. 컨트롤러는 `@CurrentAccountId`와 path의 `organizationId`를 받는다.
3. 서비스 또는 인가 helper가 organization 존재 여부를 먼저 확인한다.
4. organization이 없으면 `404`
5. 서비스 또는 인가 helper가 `accountId + organizationId` membership을 조회한다.
6. membership이 없으면 `403`
7. role이 부족하면 `403`
8. `{memberId}`가 해당 organization 소속 membership이 아니면 `404`
9. 허용된 요청만 실제 비즈니스 로직으로 넘긴다.

## 최소 인가 적용 우선순위
- P1
  - 멤버 목록 조회
  - 멤버 추가
  - 멤버 role 변경
  - 멤버 제거
- P2
  - API Key rotate
  - route template / event type mapping admin 관리

## role 기준(1차)
- `OWNER`
  - 멤버 조회/추가/변경/제거 가능
  - 조직 핵심 설정 변경 가능
- `ADMIN`
  - 멤버 조회 가능
  - 조직 설정 일부 수정 가능
  - 멤버 추가/변경/제거는 불가
- `VIEWER`
  - 읽기 전용

## account 상태와 membership 부재의 차이
- account가 `DISABLED`면 account 차원에서 접근 차단
  - `403`
- account는 `ACTIVE`지만 해당 organization membership이 없으면
  - `403`
- membership은 있지만 role이 부족하면
  - `403`

즉 인증 성공 이후의 대부분 차단은 17단계에서 `403`으로 정리한다.

---

# 17.8 실행 순서

## 1) 정책 고정
1. organization context 전달 방식 확정
2. role 모델 확정
3. organization 생성 시 creator owner 연결 규칙 확정
4. 마지막 owner 보호 규칙 확정

## 2) 도메인 추가
1. `OrganizationRole`
2. `OrganizationMember`
3. migration 작성

## 3) 계정/조직 생성 흐름 정리
1. 회원가입 API 추가
2. organization 생성 API를 JWT 기반으로 정리
3. organization 생성 시 creator owner membership 연결

## 4) 조회/응답 확장
1. `/api/v1/admin/me`에 membership 정보 추가
2. membership repository / query 추가

## 5) 멤버 관리 API
1. 목록 조회
2. 추가
3. role 변경
4. 제거

## 6) 인가 적용
1. membership 조회 helper
2. role check helper
3. 멤버 API role 차단 적용

## 7) 회귀 검증
1. 인증만 성공하고 membership 없을 때 차단
2. role 부족 차단
3. 마지막 owner 보호
4. 기존 API Key 경로 회귀 확인

---

# 17.9 체크포인트

## 멤버십 모델
- 한 account가 여러 organization에 속할 수 있는지
- `(account, organization)` 중복 membership이 막히는지
- `memberId`가 `OrganizationMember.id`로 일관되게 사용되는지

## role 규칙
- `OWNER / ADMIN / VIEWER` 책임 범위가 문서와 코드에서 일치하는지
- 마지막 owner 보호가 실제로 막히는지
- 멤버 변경 계열이 `OWNER` 전용으로 유지되는지

## API 경계
- `/api/v1/admin/**`는 JWT + membership/RBAC
- `/api/events/**`, `/api/v1/events/**`는 기존 API Key
- 두 구조가 서로 섞이지 않는지

## 포트폴리오 설명성
- "왜 Account와 Organization을 직접 묶지 않았는가"를 설명할 수 있는지
- "왜 조직 컨텍스트를 path로 명시했는가"를 설명할 수 있는지
- "왜 usage/플랜은 아직 안 넣었는가"를 설명할 수 있는지

---

# 17.10 후속 작업
- usage 집계
- 플랜/요청 제한
- API Key rotate admin API
- route template / event type mapping admin 경로 RBAC 적용
- 감사 로그
- 관리자 콘솔
- Spring Security principal / 권한 모델 정식화
  - 현재 `custom filter + resolver` 구조를 `SecurityContext` 중심 구조로 옮길지 후속 단계에서 판단
