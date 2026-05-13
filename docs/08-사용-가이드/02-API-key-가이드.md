# API key 가이드

## 목적

- 제품 API를 호출할 때 사용하는 `X-API-Key`의 역할과 사용 기준을 정리한다.
- 관리자 콘솔 JWT와 제품 API key를 구분해서 이해하게 한다.

---

## 1. API key는 어디에 쓰나

- 이벤트 적재
  - `POST /api/events`
- 제품 분석 조회
  - `GET /api/v1/events/analytics/**`

예:

```bash
curl -H "X-API-Key: <API_KEY>" https://clickchecker.dev/api/v1/events/analytics/aggregates/overview
```

---

## 2. JWT와 무엇이 다른가

### 관리자 콘솔 JWT
- 로그인한 계정 기준
- 경로: `/api/v1/admin/**`
- 헤더: `Authorization: Bearer <accessToken>`

### 제품 API key
- organization machine credential 기준
- 경로:
  - `POST /api/events`
  - `GET /api/v1/events/analytics/**`
- 헤더: `X-API-Key: <API_KEY>`

즉 브라우저 콘솔은 JWT를 쓰고, 제품 API 클라이언트는 API key를 쓴다.

---

## 3. 언제 평문 key를 볼 수 있나

- organization 생성 직후
- API key 재발급 직후

그 이후에는 평문 key를 다시 보여주지 않고, metadata만 조회한다.

---

## 4. 확인할 수 있는 metadata

- `kid`
- `apiKeyPrefix`
- `status`
- `createdAt`
- `rotatedAt`
- `lastUsedAt`

---

## 5. 문제가 생기면 무엇을 보나

### 401 Unauthorized
- key 문자열이 틀렸을 수 있다.
- 다른 organization key일 수 있다.
- 재발급 후 예전 key를 쓰고 있을 수 있다.
- status가 `ACTIVE`가 아닐 수 있다.

### 브라우저 콘솔에서는 잘 되는데 curl은 안 될 때
- 브라우저는 JWT를 쓰고 있을 수 있다.
- 제품 API 호출에는 반드시 `X-API-Key`가 필요하다.

---

## 6. 예시

```bash
curl -X POST https://clickchecker.dev/api/events \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY>" \
  -d '{
    "eventType": "page_view",
    "path": "/products/101",
    "externalUserId": "api-key-guide-user",
    "occurredAt": "2026-03-29T12:00:00Z"
  }'
```

---

## 7. 다음 문서

- [01-빠른-시작.md](01-빠른-시작.md)
- [03-이벤트-전송.md](03-이벤트-전송.md)
- [05-집계-api.md](05-집계-api.md)
