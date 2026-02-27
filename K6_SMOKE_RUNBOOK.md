# k6 Smoke Runbook (0단계)

## 0. 목적
- `k6/smoke-read.js`, `k6/smoke-write.js`를 동일한 조건으로 반복 실행한다.
- p95/p99, 실패율, RPS를 기준으로 성능 기준선을 남긴다.

## 1. 사전 준비
1. 인프라 기동 (DB/Prometheus/Grafana)
```bash
docker compose up -d postgres prometheus grafana
```

2. 애플리케이션 로컬 실행 (개발 기본 방식)
```bash
./gradlew bootRun
```

3. 헬스 체크
```bash
curl -sS http://localhost:8080/actuator/health
```

## 2. 테스트 데이터 준비
- write 시나리오는 직접 데이터를 적재한다.
- read 시나리오는 조회 대상 조직(`ORG_ID`)에 데이터가 있어야 의미 있는 결과가 나온다.

## 3. Read-heavy 실행
```bash
BASE_URL=http://localhost:8080 \
ORG_ID=1 \
FROM=2026-02-13T00:00:00 \
TO=2026-02-14T00:00:00 \
TOP=5 \
k6 run k6/smoke-read.js
```

## 4. Write-heavy 실행
```bash
BASE_URL=http://localhost:8080 \
ORG_ID=1 \
EVENT_TYPE=click \
k6 run k6/smoke-write.js
```

## 5. 결과 확인 포인트
- k6 콘솔
  - `http_req_failed`
  - `http_req_duration p(95), p(99)`
  - `iterations`, `vus`

- Grafana (http://localhost:3000)
  - RPS
  - p95 / p99
  - 4xx / 5xx 비율

- Prometheus 타깃 상태
```bash
curl -sS http://localhost:9090/api/v1/targets
```
  - `click-checker-app` 타깃이 `health: up`인지 확인

## 6. 비교 시 고정 조건 (반드시 동일)
- 동일 RPS
- 동일 duration
- 동일 warm-up 정책
- 동일 ORG_ID / 기간 조건
- 동일 코드 버전(커밋 해시)

## 7. 실패 시 빠른 점검
1. 앱 미기동
```bash
curl -sS http://localhost:8080/actuator/health
```
2. Prometheus 미수집
```bash
docker compose logs --tail=100 prometheus
```
3. DB 연결 문제
```bash
docker compose logs --tail=100 postgres
```

## 8. 기준선 기록 (Baseline #1)
- 실행일: 2026-02-27
- 실행 환경: `docker network(click-checker_default) + grafana/k6 컨테이너`
- 대상 앱: `http://app:8080`
- 공통 조건:
  - executor: `constant-arrival-rate`
  - rate: `20 req/s`
  - duration: `2m`

### 8.1 Write-heavy 결과
- script: `k6/smoke-write.js`
- `http_req_failed`: `0.00%` (0/2401)
- `http_req_duration p(95)`: `29.7ms`
- `http_req_duration p(99)`: `118.52ms`
- `http_reqs`: `2401` (`19.91 req/s`)

### 8.2 Read-heavy 결과
- script: `k6/smoke-read.js`
- `http_req_failed`: `0.00%` (0/2400)
- `http_req_duration p(95)`: `22.44ms`
- `http_req_duration p(99)`: `42.25ms`
- `http_reqs`: `2400` (`19.91 req/s`)

### 8.3 비교 메모
- 현재 기준선에서는 read-heavy가 write-heavy보다 지연이 낮다.
- 두 시나리오 모두 threshold 통과:
  - `http_req_failed < 1%`
  - `p95 < 500ms`
  - `p99 < 1000ms`

## 9. 기준선 기록 템플릿 (Baseline #2)
- 실행일: `YYYY-MM-DD`
- 실행 환경: `로컬 앱 or docker app`, `k6 실행 방식(로컬/컨테이너)`
- 대상 앱: `BASE_URL`
- 공통 조건:
  - executor: `constant-arrival-rate`
  - rate: `__ req/s`
  - duration: `__`
  - warm-up 정책: `__`

### 9.1 Write-heavy 결과
- script: `k6/smoke-write.js`
- `http_req_failed`: `__%` (`__/__`)
- `http_req_duration p(95)`: `__ms`
- `http_req_duration p(99)`: `__ms`
- `http_reqs`: `__` (`__ req/s`)

### 9.2 Read-heavy 결과
- script: `k6/smoke-read.js`
- `http_req_failed`: `__%` (`__/__`)
- `http_req_duration p(95)`: `__ms`
- `http_req_duration p(99)`: `__ms`
- `http_reqs`: `__` (`__ req/s`)

### 9.3 Baseline #1 대비 비교
- Write p95: `__ -> __` (`+/- __ms`)
- Write p99: `__ -> __` (`+/- __ms`)
- Read p95: `__ -> __` (`+/- __ms`)
- Read p99: `__ -> __` (`+/- __ms`)
- 실패율 변화: `__`
- 해석/원인 가설:
  - `__`
