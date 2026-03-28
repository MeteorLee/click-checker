# 24. Quick Start 페이지 계획

## 목표
- Click-Checker를 처음 보는 사용자가 콘솔 밖 문서 없이도 바로 시작할 수 있는 안내 페이지를 추가한다.
- 관리자 콘솔용 API와 제품/API key 기반 분석 API의 차이를 한 화면에서 짧게 설명한다.
- 최종 목표는 `회원가입 -> organization 생성 -> API key 확인 -> 이벤트 전송 -> 분석 확인` 흐름을 5분 안에 따라가게 만드는 것이다.

---

## 24.1 왜 필요한가

- 현재는 콘솔 화면과 API가 충분히 열려 있지만, 처음 쓰는 사람 입장에서는 시작 순서가 보이지 않는다.
- 특히 아래 두 축이 분리되어 있어 처음엔 헷갈릴 수 있다.
  - 관리자 콘솔 API: `JWT` 기반, `/api/v1/admin/**`
  - 제품 API: `X-API-Key` 기반, `/api/events`, `/api/v1/events/analytics/**`
- 따라서 Quick Start는 기능 소개보다 `어디서 키를 받고, 어떤 API를 먼저 호출해야 하는지`를 짧게 정리하는 페이지여야 한다.

---

## 24.2 페이지 역할

Quick Start 페이지는 아래 역할만 맡는다.

1. 시작 순서를 보여준다.
- 회원가입 또는 로그인
- organization 생성
- API key 확인
- 이벤트 1건 전송
- 분석 API 또는 콘솔 화면 확인

2. 관리자 콘솔 API와 제품 API를 구분해 준다.
- 콘솔은 브라우저에서만 사용
- 제품 API는 API key로 직접 호출

3. 바로 복사해서 쓸 수 있는 예시를 준다.
- `curl`
- 최소 JSON payload
- 최소 분석 조회 예시

4. 다음 화면으로 이동시키는 허브 역할을 한다.
- `/organizations`
- 특정 organization dashboard
- API key settings

---

## 24.3 위치와 진입 방식

### 권장 경로
- `/getting-started`

### 접근 정책
- 로그인 전에도 페이지 자체는 볼 수 있어도 된다.
- 다만 organization별 API key, 실제 예시 값, dashboard 진입 버튼은 로그인 후에 더 풍부하게 보여주는 방식이 자연스럽다.

### 대안
- `/dashboard/[organizationId]/getting-started`

대안은 organization 문맥을 바로 쓸 수 있다는 장점이 있지만, 로그인 전 입구 역할은 약하다.  
따라서 1차는 `/getting-started`를 권장한다.

---

## 24.4 1차 포함 범위

### 포함
- 서비스 한 줄 소개
- 시작 단계 4~5개
- API key 발급 위치 안내
- 이벤트 전송 예시 1개
- 분석 조회 예시 2개
  - overview
  - activity 또는 users
- 콘솔에서 어디를 보면 되는지 안내

### 제외
- SDK 설치 가이드
- 언어별 예제 다변화
- 토큰 기반 초대
- 조직 상태 모델
- 실제 코드 실행 playground

---

## 24.5 페이지 구성 초안

### 1. Hero
- 제목: `5분 안에 Click-Checker 시작하기`
- 설명:
  - organization을 만들고
  - API key를 받고
  - 이벤트를 보내고
  - 분석 결과를 확인하는 최소 흐름

### 2. 시작 단계
- `1. 로그인 또는 회원가입`
- `2. organization 만들기`
- `3. API key 확인`
- `4. 이벤트 1건 보내기`
- `5. overview / activity 확인`

### 3. API 구분 안내
- `관리자 콘솔 API`
  - JWT
  - 브라우저/운영 화면용
- `제품 API`
  - X-API-Key
  - 이벤트 적재/분석 조회용

### 4. 이벤트 전송 예시
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY>" \
  -d '{
    "eventType": "page_view",
    "path": "/",
    "externalUserId": "quick-start-user-1",
    "occurredAt": "2026-03-28T12:00:00Z"
  }'
```

### 5. 분석 조회 예시
```bash
curl "http://localhost:8080/api/v1/events/analytics/aggregates/overview?from=2026-03-28T00:00:00Z&to=2026-03-29T00:00:00Z" \
  -H "X-API-Key: <API_KEY>"
```

```bash
curl "http://localhost:8080/api/v1/events/analytics/activity?from=2026-03-01T00:00:00Z&to=2026-03-29T00:00:00Z&timezone=Asia/Seoul" \
  -H "X-API-Key: <API_KEY>"
```

### 6. 다음 단계
- dashboard에서 overview 보기
- Route / Event Type 규칙 관리
- demo organization 추가

---

## 24.6 데이터/권한 의존성

- 로그인 상태면 `/api/v1/admin/me`로 membership 목록을 읽을 수 있어야 한다.
- organization 생성 직후 API key를 1회 확인할 수 있어야 한다.
- demo org가 있으면 Quick Start에서 demo 진입도 함께 안내할 수 있다.
- 제품 API 예시는 실제로 현재 public endpoint와 맞아야 한다.
  - `POST /api/events`
  - `GET /api/v1/events/analytics/aggregates/overview`
  - `GET /api/v1/events/analytics/activity`

---

## 24.7 구현 방향

- 1차는 문서형 페이지로 시작한다.
- 페이지 안에서 과한 인터랙션보다, 복사 가능한 예시와 명확한 단계 안내를 우선한다.
- 색/카드 스타일은 현재 `/`와 `/login`의 바깥 화면 톤을 따라간다.
- 실제 organization이 있는 로그인 사용자에게는 버튼을 더 많이 보여주고,
  비로그인 상태에선 가입/로그인 CTA를 우선 보여준다.

---

## 24.8 완료 기준

- 새로운 사용자가 Quick Start 페이지만 보고 첫 이벤트를 보낼 수 있다.
- 관리자 콘솔 API와 제품 API의 차이를 페이지 안에서 설명할 수 있다.
- overview 또는 activity API까지 확인하는 예시가 포함된다.
- `/`, `/login`, `/signup`, `/organizations`와 어색하지 않게 연결된다.
