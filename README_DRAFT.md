# Click Checker (Draft)

멀티테넌트 B2B 이벤트 수집/집계 백엔드 프로젝트입니다.  
고객사별 사용자 행동 로그를 수집하고, 경로/시간 축으로 집계해 분석 지표를 제공합니다.

## 1. 문제 정의
- 고객사 서비스에서 발생하는 이벤트 로그를 안정적으로 수집해야 한다.
- 고객사 간 데이터가 절대 섞이면 안 된다(멀티테넌트 격리).
- 단순 저장을 넘어, 운영/분석에 바로 쓰는 집계 API가 필요하다.

## 2. 해결 범위
- 수집:
  - `POST /api/organizations`
  - `POST /api/event-users`
  - `POST /api/events`
- 집계:
  - `GET /api/events/aggregates/paths` (Top-N path)
  - `GET /api/events/aggregates/time-buckets` (HOUR/DAY)

## 3. 핵심 도메인 모델
- `Organization`: 고객사(테넌트) 단위
- `EventUser`: organization 범위 사용자 식별자(`externalUserId`)
- `Event`: 사용자 행동 로그(`eventType`, `path`, `occurredAt`, `payload`)

도메인 관계:
- `Organization 1 - N EventUser`
- `Organization 1 - N Event`
- `EventUser 0..1 - N Event`

## 4. 핵심 의사결정
### 4.1 내부 PK 노출 제거
- 기존: 이벤트 생성/조회에서 `eventUserId`(내부 DB PK) 사용
- 변경: `externalUserId` 기반 계약으로 전환
- 이유:
  - 고객사는 내부 PK를 알 수 없음
  - API 계약 안정성/보안/연동 편의성 향상

### 4.2 EventUser upsert 정책
- 이벤트 생성 시 `externalUserId`가 존재하면:
  - `(organizationId, externalUserId)`로 조회
  - 없으면 자동 생성(upsert)
- `externalUserId`가 null/blank면 익명 이벤트로 저장

### 4.3 집계 시간 규칙 통일
- 모든 집계는 `from <= occurredAt < to` 규칙 적용

## 5. API 예시
### 이벤트 생성
```json
{
  "organizationId": 1,
  "externalUserId": "u-1001",
  "eventType": "click",
  "path": "/home",
  "occurredAt": "2026-02-13T10:01:00",
  "payload": "{\"buttonId\":\"signup\"}"
}
```

### Path 집계
`GET /api/events/aggregates/paths?organizationId=1&from=2026-02-13T00:00:00&to=2026-02-14T00:00:00&top=5`

### 시간 버킷 집계
`GET /api/events/aggregates/time-buckets?organizationId=1&from=2026-02-13T00:00:00&to=2026-02-14T00:00:00&bucket=HOUR`

## 6. 테스트 전략
- 통합테스트 중심 검증:
  - 멀티테넌트 데이터 격리
  - `externalUserId` 필터 동작
  - upsert 정책
  - 시간 버킷(HOUR/DAY) 집계 정확성
  - 잘못된 입력(`from >= to`, invalid bucket) 예외 처리

## 7. 데모 실행
- 3분 데모 가이드: `DEMO_RUNBOOK.md`
- 흐름:
  1. organization 2개 생성
  2. 이벤트 적재(유저/익명)
  3. path 집계 확인
  4. time-bucket 집계 확인
  5. organization별 결과 분리 확인

## 8. 기술 스택
- Java 21
- Spring Boot 3
- Spring Data JPA + Querydsl
- PostgreSQL (local), H2 (ci test)
- Gradle

## 9. 다음 확장 계획
1. 퍼널/리텐션 집계 API 추가
2. 익명 사용자 정책 고도화(anonymousId 등)
3. 데모 UI 최소 구현(이벤트 전송 + 집계 조회)
4. 인덱스/파티셔닝 기반 성능 개선
