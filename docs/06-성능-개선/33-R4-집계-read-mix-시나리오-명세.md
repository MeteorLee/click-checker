# 33. R4 집계 read mix 시나리오 명세

## 문서 목적

`R4`는 집계 API를 개별로 반복하는 대신, 실제 화면 사용 흐름에 가까운 **집계 read mix**를 보기 위한 시나리오다.  
이번 문서는 `R4`의 `core / extended` 계층, 각 단계(`v1 / v2 / v3`)의 비율, 그리고 메인 집계 API 범위를 고정하는 데 목적이 있다.

이번 문서는 아직 seed 상세와 prepare / run 구현까지 고정하지 않는다.  
현재 문서가 고정하는 것은 **API 계층, mix 비율, request preset, rate ladder, duration, threshold, success / saturation 기준, 공통 dataset / snapshot 재사용 원칙**까지다.

## 적용 범위

- 대상 시나리오:
  - `R4`
- 성격:
  - 집계 read mix
- 이번 문서가 다루는 범위:
  - core / extended 계층
  - 단계별 mix 비율
  - `R4`에서 아직 포함하지 않는 API

## 공통 기준에서 재사용하는 항목

- [30-현실형-부하-테스트-v2-계획.md](30-현실형-부하-테스트-v2-계획.md)의 v2 공통 tenant 분포를 그대로 사용한다.
- [37-v2-공통-dataset-및-snapshot-설계.md](37-v2-공통-dataset-및-snapshot-설계.md)의 공통 dataset / snapshot 설계를 그대로 사용한다.
- [03-대규모-부하-테스트-런북.md](03-대규모-부하-테스트-런북.md)의 환경 단계, 산출물, 기록 원칙을 재사용한다.

## 이전 시나리오와 다른 점

- `R1`, `R2`, `R3`는 각각 하나의 read 축을 따로 봤다.
- `R4`는 집계 API 여러 개를 계층형으로 섞는다.
- 즉 `R4`는 집계 read의 realistic baseline에 더 가깝다.

## 이번 시나리오에서 새로 중요해지는 변수

- 집계 API 간 비중 차이
- core와 extended가 섞일 때의 복잡도 증가
- unique-users 계열과 교차 time-buckets 계열이 전체 read에 미치는 영향

## API 계층 정의

### core

- `overview`
- `routes`
- `event-types`
- `time-buckets`

### extended

- `route-event-types`
- `route-time-buckets`
- `event-type-time-buckets`
- `route-event-type-time-buckets`
- `routes/unique-users`
- `event-types/unique-users`

### 현재 메인 범위에서 제외하는 운영 보조 API

- `raw-event-types`
- `paths`
- `routes/unmatched-paths`

이 세 API는 운영 보조 성격이 강하므로, 첫 `R4` 메인 mix에는 포함하지 않는다.

## 단계별 mix 구조

### `R4 v1`

- core only
- 내부 비율:
  - `overview 40`
  - `routes 30`
  - `event-types 20`
  - `time-buckets 10`

### `R4 v2`

- `core 70`
- `extended 30`

내부 분배:

- core
  - `overview 25`
  - `routes 20`
  - `event-types 15`
  - `time-buckets 10`
- extended
  - `route-event-types 10`
  - `route-time-buckets 5`
  - `event-type-time-buckets 5`
  - `route-event-type-time-buckets 5`
  - `unique-users 계열 5`

### `R4 v3`

- `core 50`
- `extended 50`

원칙:

- `R4 v3`는 v1, v2보다 더 무거운 집계 mix를 보는 단계다.
- `extended only`는 현재 기본 시나리오로 만들지 않는다.

## 환경 고정값 / 변경값

### 현재 고정값

- tenant 분포:
  - `6개 org / 50-30-20`
- 계층:
  - `core / extended`
- 단계:
  - `v1 / v2 / v3`
- 부하 세기는 별도 `stage 1 / 2 / 3`로 본다.
- 즉 `R4 v1 + stage 3` 같은 조합도 허용한다.
- 운영 보조 API 제외
- 공통 query 기본값:
  - `externalUserId = null`
  - `timezone = UTC`
- `default / heavy` 매핑:
  - `v1`, `v2`는 `default`
  - `v3`는 `heavy`
- rate ladder:
  - `10 / 20 / 30`
- threshold:
  - `p95 < 3000ms`
  - `p99 < 5000ms`
- 기본 reset mode:
  - `skip`
  - 단, 직전 write run의 상태 오염을 지워야 할 때는 `full` 또는 `quick`을 명시적으로 사용한다.
  - 현재 구현에서는 직전 dataset state가 dirty면 prepare가 자동으로 `quick`으로 승격될 수 있다.

## VU

- `10`
  - `preAllocatedVUs = 20`
  - `maxVUs = 60`
- `20`
  - `preAllocatedVUs = 40`
  - `maxVUs = 120`
- `30`
  - `preAllocatedVUs = 60`
  - `maxVUs = 180`

## request preset

`R4`의 request preset은 `default / heavy` 두 층으로 나눈다.

### 공통 기준

- `routes`, `event-types`, `route-event-types`, `routes/unique-users`, `event-types/unique-users`의 `top`은 모두 `10`으로 고정한다.
- `eventType` 필터는 기본적으로 비워 둔다.
- 시계열 bucket은 기간 길이에 따라 결정한다.

### default

- 비시계열 집계 API:
  - `최근 24시간`
- 시계열 집계 API:
  - `최근 24시간`
  - `bucket = HOUR`

즉 아래 API들은 default에서 모두 `24시간` 기준으로 본다.

- `overview`
- `routes`
- `event-types`
- `route-event-types`
- `routes/unique-users`
- `event-types/unique-users`
- `time-buckets`
- `route-time-buckets`
- `event-type-time-buckets`
- `route-event-type-time-buckets`

### heavy

- 비시계열 집계 API:
  - `최근 7일`
- 시계열 집계 API:
  - `최근 7일`
  - `bucket = DAY`

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

- `raw-event-types`, `paths`, `routes/unmatched-paths`는 메인 mix에서 제외한다.
- `extended only`는 첫 버전에서 만들지 않는다.

## 이전 시나리오의 어떤 가정을 가져오지 않는지

- `R1`의 `overview` 단일 반복 가정은 가져오지 않는다.
- `R2`의 `routes` 단일 반복 가정은 가져오지 않는다.
- `R3`의 `time-buckets` 단일 반복 가정은 가져오지 않는다.

## 해석 시 주의사항

- `R4`는 v1보다 현실성이 높지만, 어떤 API가 정확히 원인인지 분리하기는 더 어렵다.
- 따라서 `R4`는 `R1/R2/R3` 결과와 함께 읽어야 한다.
- `v1 -> v2 -> v3` 비교를 통해 extended 비중이 늘 때의 변화를 본다.

## 다음에 정할 항목

- seed 상세
- prepare / run 구조

## 이번 문서의 결론

> `R4`는 집계 API를 `core / extended` 계층으로 나누고, `v1 / v2 / v3` 단계별 mix 비율로 realistic read를 보는 집계 read mix 시나리오다.
