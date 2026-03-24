# 29. Prod Public 1차 종합

## 문서 목적

`28-Prod-Public-검증-계획.md`에서 시작한 prod-public 검증 결과를 한 문서로 정리한다.

이번 문서는 public run 결과를 세부 요청 단위로 길게 늘어놓기보다,
**local -> prod-direct -> prod-public** 흐름에서 대표 안정 구간이 실제 공개 진입점에서도 유지되는지 판단하는 데 목적이 있다.

## 1. 이번에 실제로 확인한 것

이번 prod public 단계에서 실제로 확인한 항목은 아래 세 개다.

- `W1 r100`
- `R1 r30`
- `M1 30/30`

즉 이번 단계는 새 한계 구간을 찾는 단계라기보다,
**public 계층이 기존 안정 구간을 깨는지 확인하는 최종 validation 단계**에 가깝다.

## 2. 핵심 결과

핵심 결과는 아래처럼 정리된다.

- `W1 r100`
  - `p95 25.84ms`
  - `p99 92.2ms`
  - `http_req_failed 0%`
- `R1 r30`
  - `p95 388.46ms`
  - `p99 620.17ms`
  - `http_req_failed 0%`
- `M1 30/30`
  - read `p95 194.25ms`, `p99 283.41ms`
  - write `p95 61.75ms`, `p99 92.01ms`
  - `http_req_failed 0%`

즉 public 도메인 기준에서도 대표 stable baseline은 모두 유지됐다.

## 3. 이번에 확인한 의미

이번 단계의 의미는 아래 세 가지다.

### 3.1 public 계층이 stable baseline을 깨지 않았다

`nginx + TLS + public route`가 추가돼도 아래 안정 구간은 그대로 유지됐다.

- `W1 r100`
- `R1 r30`
- `M1 30/30`

즉 지금까지 local과 prod-direct에서 확보한 stable baseline은
공개 진입점 기준에서도 유효하다고 볼 수 있다.

### 3.2 public 오버헤드는 있었지만 해석 가능한 범위였다

특히 `R1 r30`은 prod-direct보다 지연이 올라갔다.

하지만 threshold를 깰 정도는 아니었고,
public 계층이 들어가며 생기는 자연스러운 상승 후보로 해석할 수 있다.

즉 이번 단계에서는
**public 계층 추가 비용은 보였지만, 안정 구간 자체를 무너뜨리지는 않았다**고 보는 편이 맞다.

### 3.3 성능 개선 1차 결과의 신뢰도가 올라갔다

이제 대표 안정 구간은 아래 세 환경에서 모두 확인된 셈이다.

- local
- prod-direct
- prod-public

즉 성능 개선 1차에서 정리한 baseline과 병목 지도는
단순 local 착시 수준을 넘어서 운영 공개 경로까지 포함한 검증을 거쳤다고 볼 수 있다.

## 4. 이번 단계에서 남겨둔 것

이번 단계에서 일부러 남겨둔 것은 아래와 같다.

### 4.1 public failure-side 구간

아직 확인하지 않은 것:

- `R1 r50`
- `M1 40/40`

즉 public 경로에서의 failure-side 위치는 아직 직접 찍지 않았다.

### 4.2 automated Grafana capture

prod Grafana renderer timeout 이슈는 그대로 남아 있다.

따라서 이번 public 단계에서도:

- `capture.mode=manual`
- scripted PNG 자동 생성 보류

로 유지했다.

## 5. 지금 기준 판단

지금 기준 판단은 아래처럼 정리하는 것이 자연스럽다.

- stable baseline은 public 경로에서도 유지됐다.
- `prod-direct`에서 잡은 `R1`, `M1` limit 정보와 모순되는 신호는 없었다.
- public 계층 오버헤드는 존재하지만 현재 안정 구간을 뒤집을 정도는 아니었다.

즉 현재 단계는
**local -> prod-direct -> prod-public까지 1차 validation을 마쳤다**고 보는 것이 맞다.

## 6. 다음 단계

다음 단계 후보는 두 갈래다.

### 6.1 구조 개선 우선순위 재판단

지금까지 확보한 결과를 기준으로:

- write 본체 비용
- overview 조합 비용
- mixed shared resource 경쟁

중 무엇을 먼저 더 손댈지 다시 정리한다.

### 6.2 필요 시 public failure-side 추가 확인

필요하면 아래를 추가 확인한다.

- `R1 r50`
- `M1 40/40`

이건 public 경로에서 실제 failure-side 위치까지 더 보고 싶을 때 가는 단계다.

## 7. 관련 문서

- 계획 문서:
  - [28-Prod-Public-검증-계획.md](28-Prod-Public-검증-계획.md)
- `prod-direct` 종합:
  - [27-Prod-Direct-1차-종합.md](27-Prod-Direct-1차-종합.md)
- 실행 기록:
  - [04-대규모-부하-테스트-기록.md](04-대규모-부하-테스트-기록.md)
- 전체 종합:
  - [07-성능-개선-종합.md](07-성능-개선-종합.md)

## 결론

이번 prod public 1차 검증의 결론은 아래 한 줄로 정리할 수 있다.

> prod-public에서도 `W1 r100`, `R1 r30`, `M1 30/30`은 모두 안정 구간으로 확인됐다. 즉 local과 prod-direct에서 확보한 stable baseline은 실제 공개 진입점 기준으로도 유효하다.
