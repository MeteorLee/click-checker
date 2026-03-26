# API 연동 (v1.0)

## 목표
- 1차 프런트 콘솔에서 실제로 사용할 API 계약을 프런트 관점으로 정리한다.
- 이번 문서는 `로그인 -> organization 선택 -> overview` 흐름에 필요한 API만 다룬다.
- 백엔드 전체 API 문서를 다시 쓰기보다, 프런트에서 직접 사용할 요청/응답과 에러 처리 기준만 추린다.

---

## 1. 범위

이번 문서에서 다루는 API:
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/me`
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`

이번 문서에서 제외하는 API:
- signup / refresh / logout
- member 관리
- API key 조회 / rotate
- routes / event-types / trends / users / activity / funnels / retention

설명:
- 1차 프런트는 최소 콘솔 흐름을 닫는 것이 목적이다.
- 따라서 실제 화면에서 바로 붙일 API만 먼저 정리한다.

---

## 2. 공통 원칙

## 2.1 인증 방식
- 브라우저는 admin JWT만 사용한다.
- 프런트는 `Authorization: Bearer <accessToken>` 헤더를 사용한다.
- API key는 브라우저에 올리지 않는다.

## 2.1.1 로컬 개발 기준
- frontend 개발 서버는 `http://localhost:3001`을 사용한다.
- backend API는 `http://localhost:8080`을 사용한다.

## 2.2 에러 처리 공통 원칙
- `401`
  - access token 없음 또는 만료/무효
  - 프런트는 로그인 상태를 해제하고 `/login`으로 이동한다.
- `403`
  - 로그인은 되었지만 organization 접근 권한이 없음
  - 프런트는 접근 불가 상태를 보여준다.
- `404`
  - 요청한 organization이 없음
  - overview 화면에서 organization 없음 상태를 보여준다.
- `5xx`
  - 일반 서버 오류 상태를 보여준다.

## 2.3 날짜 처리 공통 원칙
- 프런트는 overview 조회 시 `from`, `to`를 날짜 문자열로 보낸다.
- timezone 파라미터는 보내지 않는다.
- 날짜 해석은 백엔드 정책에 따라 `Asia/Seoul` 기준으로 처리된다.

---

## 3. 로그인

## 3.1 요청
- `POST /api/v1/admin/auth/login`

예상 요청 본문:

```json
{
  "loginId": "admin",
  "password": "password"
}
```

설명:
- 프런트는 로그인 폼에서 `loginId`, `password`를 입력받는다.
- 요청 본문은 JSON으로 보낸다.

## 3.2 성공 응답

프런트가 실제로 필요한 핵심:
- `accessToken`

설명:
- 응답 본문에 refresh token 관련 필드가 있더라도 1차 프런트에서는 우선 access token 확보가 핵심이다.
- 로그인 성공 후 프런트는 access token을 저장하고 `/organizations`로 이동한다.

## 3.3 실패 시 처리
- `401` 또는 로그인 실패 응답:
  - 로그인 실패 메시지 표시
  - 현재 화면 유지

프런트 처리 기준:
- 비밀번호 오류, 존재하지 않는 계정 등은 하나의 일반 로그인 실패 메시지로 묶는다.

---

## 4. 현재 사용자 / organization 목록

## 4.1 요청
- `GET /api/v1/admin/me`

헤더:

```text
Authorization: Bearer <accessToken>
```

## 4.2 성공 응답

프런트가 실제로 필요한 핵심:
- account 기본 정보
- memberships 목록
  - `organizationId`
  - `organizationName`
  - `role`

설명:
- organization 선택 화면은 memberships 목록만 있으면 만들 수 있다.
- 1차에서는 account 세부 정보보다 "접근 가능한 organization 목록"이 더 중요하다.

## 4.3 프런트 사용 방식
- 페이지 진입 시 `/me`를 호출한다.
- memberships를 카드 목록으로 렌더링한다.
- 사용자가 organization 하나를 고르면 `/dashboard/[organizationId]`로 이동한다.

## 4.4 실패 시 처리
- `401`
  - token 무효 또는 만료
  - `/login`으로 이동
- 그 외 오류
  - organization 목록 로드 실패 상태 표시

## 4.5 빈 상태 처리
- memberships가 비어 있으면
  - "접근 가능한 organization이 없음" 상태 표시
  - 1차에서는 생성/초대 흐름은 제공하지 않는다

---

## 5. Admin Overview

## 5.1 요청
- `GET /api/v1/admin/organizations/{organizationId}/analytics/overview`

경로 변수:
- `organizationId`

쿼리 파라미터:
- `from`
- `to`

예시:

```text
/api/v1/admin/organizations/1/analytics/overview?from=2026-03-01&to=2026-03-26
```

헤더:

```text
Authorization: Bearer <accessToken>
```

설명:
- 프런트는 organization 선택 화면에서 얻은 `organizationId`를 path에 넣는다.
- 기간은 날짜 문자열 기준으로 전달한다.
- timezone 파라미터는 보내지 않는다.

## 5.2 성공 응답

프런트가 실제로 필요한 핵심:
- 총 이벤트 수
- 고유 사용자 수
- 식별 이벤트 비율
- 이전 기간 대비 비교 정보
- top routes
- top event types

설명:
- 응답 전체 필드를 모두 1차 화면에 표시할 필요는 없다.
- 우선 KPI 카드와 요약 리스트를 만드는 데 필요한 필드만 사용한다.

## 5.3 프런트 사용 방식
- overview 페이지 진입 시 기본 기간을 정한다.
- 예:
  - 최근 7일
  - 최근 30일
- 해당 기간으로 overview API를 호출한다.
- 응답을 카드/리스트로 렌더링한다.

## 5.4 실패 시 처리
- `401`
  - `/login`으로 이동
- `403`
  - organization 접근 불가 상태 표시
- `404`
  - organization 없음 상태 표시
- 그 외 오류
  - 일반 에러 상태 표시

## 5.5 빈 결과 처리
- 응답은 성공했지만 이벤트 데이터가 거의 없을 수 있다.
- 이 경우 overview 카드 값은 0 또는 빈 리스트로 표시될 수 있다.
- 프런트는 이를 오류로 처리하지 않고 정상 빈 상태로 표시한다.

---

## 6. 프런트 구현 메모

## 6.1 API helper 분리
- `frontend/src/lib/api/auth.ts`
  - login
  - me
- `frontend/src/lib/api/analytics.ts`
  - overview

원칙:
- 페이지에서 `fetch` URL을 직접 쓰지 않는다.
- API 호출은 `lib/api`로 모은다.

## 6.2 인증 헤더 처리
- access token 저장 위치는 1차에서 단순하게 간다.
- 우선은 token을 읽어 Authorization 헤더를 붙이는 공용 helper를 둔다.

## 6.3 리다이렉트 처리
- 보호 화면에서 `401`이 발생하면 `/login`으로 이동한다.
- organization 화면과 overview 화면은 로그인 상태를 전제로 한다.

---

## 7. 1차에서 의도적으로 하지 않는 것

- refresh token 자동 갱신
- API 응답 전체 타입의 완전한 모델링
- React Query / Zustand 같은 별도 상태관리 도구 도입
- routes / event-types / trends 전용 API 연동

설명:
- 지금 단계의 목적은 최소 콘솔 흐름을 닫는 것이다.
- 복잡한 클라이언트 인프라는 후속 단계로 미룬다.

---

## 8. 구현 순서 연결

1. 로그인 API 연결
2. `/me` 연동
3. organization 선택 화면 연결
4. admin overview API 연결
5. loading / empty / error 상태 정리

설명:
- API 연동도 화면 흐름 순서대로 붙인다.
- 앞 흐름이 닫혀야 다음 흐름이 자연스럽게 이어진다.
