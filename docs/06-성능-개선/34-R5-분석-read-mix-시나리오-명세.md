# 34. R5 분석 read mix 시나리오 명세

## 문서 목적

`R5`는 `users/overview`, `funnels/report`, `retention/daily`, `retention/matrix`를 현실형으로 섞어 보는 **분석 read mix** 시나리오다.  
이번 문서는 분석 API의 `core / extended` 계층과 `v1 / v2 / v3` mix 비율을 고정하는 데 목적이 있다.

이번 문서는 아직 seed 상세와 prepare / run 구현까지 고정하지 않는다.  
현재 문서가 고정하는 것은 **분석 API 범위, mix 비율, request preset, rate ladder, duration, threshold, success / saturation 기준, 공통 dataset / snapshot 재사용 원칙**까지다.

## 적용 범위

- 대상 시나리오:
  - `R5`
- 성격:
  - 분석 read mix
- 이번 문서가 다루는 범위:
  - core / extended 계층
  - 단계별 mix 비율
  - 분석 API의 현실형 포함 방식

## 공통 기준에서 재사용하는 항목

- [30-현실형-부하-테스트-v2-계획.md](30-현실형-부하-테스트-v2-계획.md)의 v2 공통 tenant 분포를 그대로 사용한다.
- [37-v2-공통-dataset-및-snapshot-설계.md](37-v2-공통-dataset-및-snapshot-설계.md)의 공통 dataset / snapshot 설계를 그대로 사용한다.
- [03-대규모-부하-테스트-런북.md](03-대규모-부하-테스트-런북.md)의 환경 단계, 산출물, 기록 원칙을 재사용한다.

## 이전 시나리오와 다른 점

- v1에는 `funnel`, `retention`을 명시적으로 포함한 read mix 시나리오가 없었다.
- `R5`는 집계 API가 아니라 **분석 API 자체를 현실형 read 대상으로 올린 첫 시나리오**다.

## 이번 시나리오에서 새로 중요해지는 변수

- 사용자 중심 분석 API의 계산 비용
- `retention/matrix`의 확장 비용
- `funnel`과 `retention`이 집계 API보다 더 무거운지 여부

## API 계층 정의

### core

- `users/overview`
- `funnels/report`
- `retention/daily`

### extended

- `retention/matrix`

## 단계별 mix 구조

### `R5 v1`

- `users/overview 25`
- `funnels/report 35`
- `retention/daily 40`

### `R5 v2`

- `users/overview 20`
- `funnels/report 30`
- `retention/daily 30`
- `retention/matrix 20`

### `R5 v3`

- `users/overview 10`
- `funnels/report 30`
- `retention/daily 20`
- `retention/matrix 40`

원칙:

- `retention/matrix`는 처음부터 메인 비중으로 넣지 않는다.
- `v1 -> v2 -> v3`로 갈수록 더 무거운 분석 비중을 높인다.

## 환경 고정값 / 변경값

### 현재 고정값

- tenant 분포:
  - `6개 org / 50-30-20`
- 계층:
  - `core / extended`
- 단계:
  - `v1 / v2 / v3`
- 부하 세기는 별도 `stage 1 / 2 / 3`로 본다.
- 즉 `R5 v2 + stage 1`, `R5 v1 + stage 3` 같은 조합도 허용한다.
- 공통 query 기본값:
  - `externalUserId = null`
  - `timezone = UTC`
- `default / heavy` 매핑:
  - `v1`, `v2`는 `default`
  - `v3`는 `heavy`
- rate ladder:
  - `5 / 10 / 15`
- threshold:
  - `p95 < 5000ms`
  - `p99 < 8000ms`
- 기본 reset mode:
  - `skip`
  - 단, 직전 write run의 상태 오염을 지워야 할 때는 `full` 또는 `quick`을 명시적으로 사용한다.
  - 현재 구현에서는 직전 dataset state가 dirty면 prepare가 자동으로 `quick`으로 승격될 수 있다.

## VU

- `5`
  - `preAllocatedVUs = 20`
  - `maxVUs = 60`
- `10`
  - `preAllocatedVUs = 40`
  - `maxVUs = 120`
- `15`
  - `preAllocatedVUs = 60`
  - `maxVUs = 180`

## request preset

### `users/overview`

- default:
  - `최근 7일`
- heavy:
  - `최근 30일`

### `funnels/report`

- default:
  - `최근 7일`
- heavy:
  - `최근 30일`
- step 수:
  - `3`
- step 정의:
  - `view + routeKey=pricing`
  - `signup`
  - `purchase`
- `conversionWindowDays`:
  - `7`

### `retention/daily`

- default:
  - `최근 30일`
- heavy:
  - `최근 90일`
- `minCohortUsers`:
  - `1`

### `retention/matrix`

- default:
  - `최근 30일`
- heavy:
  - `최근 90일`
- `days`:
  - `1, 7, 14, 30`
- `minCohortUsers`:
  - `1`

## 환경별 duration

- local
  - stage 1, 2:
    - `30s / 5m / 0`
  - stage 3:
    - `30s / 3m / 0`
- prod-direct
  - stage 1, 2:
    - `15s / 1m / 0`
  - stage 3:
    - `15s / 30s / 0`
- prod-public
  - stage 1, 2:
    - `15s / 30s / 0`
  - stage 3:
    - `10s / 30s / 0`

## success / saturation 기준

- stable:
  - threshold pass
  - `error rate < 1%`
  - `dropped_iterations = 0`
  - achieved throughput `>= 95%`
- degraded:
  - threshold는 pass
  - 그러나 `dropped_iterations > 0`
  - 또는 achieved throughput `90~95%`
- saturated:
  - threshold fail
  - 또는 achieved throughput `< 90%`
  - 또는 `dropped_iterations` 지속 발생

### stage와 version 관계

- `v1 / v2 / v3`는 profile version이다.
- `stage 1 / 2 / 3`는 rate ladder다.
- 둘은 독립적이다.

### 아직 고정하지 않는 변경값

- seed 상세

## 이번 시나리오에서 일부러 단순화한 항목

- cohort drill-down
- 고급 funnel 변형
- on-or-after retention

즉 현재 구현된 최소 분석 API 범위 안에서만 realistic mix를 잡는다.

## 이전 시나리오의 어떤 가정을 가져오지 않는지

- `R4`의 집계 read 가정을 그대로 가져오지 않는다.
- `overview` 중심 가정을 분석 API mix에 그대로 대입하지 않는다.

## 해석 시 주의사항

- `R5`는 `R4`보다 결과 의미가 더 복합적이라, 같은 latency여도 해석 난도가 높다.
- 따라서 `R5` 결과는 `R4`와 직접 등치하지 않고, 별도 분석 축으로 본다.

## 다음에 정할 항목

- seed 상세
- prepare / run 구조

## 이번 문서의 결론

> `R5`는 `users/overview`, `funnel`, `retention`을 현실형으로 묶는 분석 read mix 시나리오이며, `retention/matrix`를 extended 축으로 두는 3단계 구조를 따른다.
