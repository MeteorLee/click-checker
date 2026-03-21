# 35. R6 통합 read mix 프로파일 정의

## 문서 목적

`R6`는 `R4` 집계 read mix와 `R5` 분석 read mix를 합쳐,  
`M2`가 재사용할 수 있는 **공용 combined read profile**을 정의하는 문서다.

즉 `R6`의 핵심 목적은 standalone read 시나리오를 하나 더 늘리는 것이 아니라,
**`M2` 안에서 read 정의가 중복되지 않게 만드는 것**이다.

## 적용 범위

- 대상 프로파일:
  - `R6`
- 이번 문서가 다루는 범위:
  - `R4 + R5` 결합 원칙
  - 단계 대응 관계
  - `M2`에서 어떤 버전을 재사용할지

## 공통 기준에서 재사용하는 항목

- [33-R4-집계-read-mix-시나리오-명세.md](33-R4-집계-read-mix-시나리오-명세.md)
- [34-R5-분석-read-mix-시나리오-명세.md](34-R5-분석-read-mix-시나리오-명세.md)
- [30-현실형-부하-테스트-v2-계획.md](30-현실형-부하-테스트-v2-계획.md)의 tenant 분포
- [37-v2-공통-dataset-및-snapshot-설계.md](37-v2-공통-dataset-및-snapshot-설계.md)의 공통 dataset / snapshot 설계

## 결합 원칙

`R6`는 아래 원칙으로 정의한다.

- 집계 read는 `R4`
- 분석 read는 `R5`
- `R6`는 두 묶음을 합친 공용 read profile
- `M2`는 이 중 `R6 v2`를 기본 read profile로 사용한다.

## 단계 대응 관계

### `R6 v1`

- `R4 v1`
- `R5 v1`

### `R6 v2`

- `R4 v2`
- `R5 v2`

### `R6 v3`

- `R4 v3`
- `R5 v3`

즉 `R6`는 독자적인 API 계층을 만들지 않고, `R4`와 `R5`의 **profile version pair**를 그대로 따른다.

## `M2`와의 관계

현재 합의된 기본값은 아래와 같다.

- `M2`의 read는 `R6 v2`
- `M2` 안에서 read 5%는
  - `R4 70`
  - `R5 30`

즉 `R6 v2`는 `M2`를 위한 기본 realistic read profile 역할을 한다.

## request preset 층

`R6`는 stage pair 외에 request preset도 아래 두 층을 함께 가진다.

### `R6 default`

- `R4 default`
- `R5 default`

### `R6 heavy`

- `R4 heavy`
- `R5 heavy`

즉 `R6`는 아래 두 축을 함께 가진다.

- stage:
  - `v1 / v2 / v3`
- request preset:
  - `default / heavy`

현재 합의된 범위에서는 `M2`가 `R6 v2`를 재사용한다는 점까지 고정하고,  
실행 문서에서는 필요에 따라 `default` 또는 `heavy`를 선택할 수 있게 둔다.

## stage와 version

- `R6 v1 / v2 / v3`는 combined read profile version이다.
- `stage 1 / 2 / 3`는 부하 세기 ladder다.
- 둘은 독립적이다.

## default / heavy 매핑

- standalone read-only 해석에서는
  - `R6 v1`, `R6 v2`를 `default`
  - `R6 v3`를 `heavy`
  로 본다.
- 다만 `M2`는 예외적으로 read profile version을 `R6 v2`에 고정한 채 preset만 `default / heavy`로 나눈다.

즉 `R6`는 stage와 preset을 독립적으로 둘 수 있고, `M2`는 그중 **`R6 v2 + default` / `R6 v2 + heavy`**를 재사용한다.

## rate ladder

- `10 / 15 / 20`

## threshold

- `p95 < 5000ms`
- `p99 < 8000ms`

## 기본 reset mode

- `skip`
- 단, 직전 write run의 상태 오염을 지워야 할 때는 `full` 또는 `quick`을 명시적으로 사용한다.
- 현재 구현에서는 직전 dataset state가 dirty면 prepare가 자동으로 `quick`으로 승격될 수 있다.

## VU

- `10`
  - `preAllocatedVUs = 30`
  - `maxVUs = 90`
- `15`
  - `preAllocatedVUs = 45`
  - `maxVUs = 135`
- `20`
  - `preAllocatedVUs = 60`
  - `maxVUs = 180`

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

## standalone 여부

- `R6` standalone run은 현재 기본 범위가 아니다.
- 필요하면 나중에 read-only combined probe로 추가할 수 있다.
- 그러나 현재는 **문서상 정의와 `M2` 재사용**이 우선이다.

## 이번 문서에서 아직 고정하지 않는 항목

- seed 상세
- prepare / run 구조

## 이번 문서의 결론

> `R6`는 `R4`와 `R5`를 합친 공용 combined read profile이며, 현재는 `M2`가 재사용하는 read 정의로 먼저 사용한다.
