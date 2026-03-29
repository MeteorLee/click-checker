# 집계 API

## 목적

- 제품 API 기준 집계 조회 경로를 빠르게 훑을 수 있게 한다.

---

## 1. 공통 사항

헤더:

```text
X-API-Key: <API_KEY>
```

자주 쓰는 조회 필드:

- `from`
- `to`
- 일부 API는 `timezone`
- 일부 API는 `top`, `days`, `conversionWindowDays`

---

## 2. overview

- `GET /api/v1/events/analytics/aggregates/overview`

짧은 확인용 요약이다.

주요 응답:

- `totalEvents`
- `uniqueUsers`
- `identifiedEventRate`
- `topRoutes`
- `topEventTypes`

---

## 3. activity

- `GET /api/v1/events/analytics/activity`

요일/시간대 분포를 본다.

주요 응답:

- `weekdaySummary`
- `weekendSummary`
- `dayOfWeekDistribution`
- `weekdayHourlyDistribution`
- `weekendHourlyDistribution`

---

## 4. users overview

- `GET /api/v1/events/analytics/users/overview`

식별/익명, 신규/재방문 사용자 흐름을 본다.

---

## 5. retention

- `GET /api/v1/events/analytics/retention/daily`
- `GET /api/v1/events/analytics/retention/matrix`

주의:
- `정확히 N일 뒤`가 아니라 `N일 내 재방문` 기준이다.

---

## 6. funnels

- `POST /api/v1/events/analytics/funnels/report`

단계별 전환과 이탈 구간을 본다.

주요 입력:

- `from`
- `to`
- `conversionWindowDays`
- `steps[]`

---

## 7. 추천 순서

1. `overview`
2. `activity`
3. `users overview`
4. `retention`
5. `funnels`

---

## 8. 다음 문서

- [01-빠른-시작.md](01-빠른-시작.md)
- [02-API-key-가이드.md](02-API-key-가이드.md)
- [03-이벤트-전송.md](03-이벤트-전송.md)
- [04-데이터-정규화.md](04-데이터-정규화.md)
