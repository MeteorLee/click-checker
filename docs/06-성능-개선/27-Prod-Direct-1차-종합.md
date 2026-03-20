# 27. Prod Direct 1차 종합

## 문서 목적

`26-Prod-Direct-검증-계획.md`에서 시작한 prod-direct 검증 결과를 한 문서로 정리한다.

이번 문서는 prod direct 결과를 세부 run 단위로 다시 길게 나열하기보다,
**local에서 확보한 안정 구간이 실제 EC2 / RDS 조건에서도 재현됐는지**를 빠르게 판단하는 데 목적이 있다.

## 1. 이번에 실제로 확인한 것

이번 prod direct 단계에서 실제로 확인한 항목은 아래 여덟 개다.

- `W1 r100`
- `R1 r10`
- `R1 r30`
- `R1 r50`
- `M1 10/10`
- `M1 20/20`
- `M1 30/30`
- `M1 40/40`

즉 이번 단계는 단순 stable baseline 재현을 넘어서,
**overview read와 mixed의 실제 prod-direct 여유 구간이 local과 얼마나 다른지**까지 확인한 단계다.

## 2. 핵심 결과

핵심 결과는 아래처럼 정리된다.

- `W1 r100`
  - `p95 22.19ms`
  - `p99 138.67ms`
  - `http_req_failed 0%`
- `R1 r10`
  - `p95 96.39ms`
  - `p99 138.28ms`
  - `http_req_failed 0%`
- `R1 r30`
  - `p95 184.34ms`
  - `p99 262.82ms`
  - `http_req_failed 0%`
- `R1 r50`
  - `p95 9.49s`
  - `p99 11s`
  - `dropped_iterations 154`
  - `threshold_fail`
- `M1 10/10`
  - read `p95 111.53ms`, `p99 190.20ms`
  - write `p95 44.78ms`, `p99 68.94ms`
  - `http_req_failed 0%`
- `M1 20/20`
  - read `p95 159.21ms`, `p99 284.25ms`
  - write `p95 46.43ms`, `p99 116.43ms`
  - `http_req_failed 0%`
- `M1 30/30`
  - read `p95 209.34ms`, `p99 272.24ms`
  - write `p95 55.61ms`, `p99 84.05ms`
  - `http_req_failed 0%`
- `M1 40/40`
  - read `p95 6.33s`
  - read `p99 8.32s`
  - write `p95 6.05s`
  - write `p99 7.23s`
  - `dropped_iterations 182`
  - `threshold_fail`

즉 local에서 먼저 확보한 stable baseline은 prod-direct에서도 재현됐고,
`overview`는 prod-direct에서 한계 구간이 `30과 50 사이`로 올라갔으며,
`mixed`는 prod-direct에서 `30/30`까지는 안정 구간이고 `40/40`에서는 한계 구간으로 들어갔다.

## 3. 이번에 확인한 의미

이번 단계에서 가장 중요한 의미는 아래 세 가지다.

### 3.1 local 결과가 방향성 면에서 유효했다

local에서 먼저 잡은 안정 구간이 실제 인프라에서도 그대로 성공했다.

이건 local 수치를 prod와 그대로 동일시해도 된다는 뜻은 아니지만,
적어도 **local에서 본 병목 지도와 개선 방향이 완전히 엇나가진 않았다**는 뜻에 가깝다.

### 3.2 첫 저위험 개선이 실제 인프라에서도 무의미하지 않았다

이번 prod direct에서 다시 확인한 대상은 모두 local 1차 사이클에서 안정 구간으로 정리된 run들이다.

즉 아래 조치들이 local 전용 착시가 아니었음을 간접적으로 확인했다.

- write 앞단 비용 절감
- `events (organization_id, occurred_at)` 인덱스
- overview 집계 재사용 구조
- mixed dataset / snapshot restore 구조

### 3.3 local보다 prod-direct 여유가 훨씬 컸다

특히 의미 있는 차이는 아래 두 가지다.

- local에선 `R1`이 `10과 30 사이`에서 한계였지만, prod-direct에선 `30과 50 사이`로 올라갔다.
- local에선 `M1`이 `10/10과 20/20 사이`에서 한계였지만, prod-direct에선 `30/30과 40/40 사이`로 올라갔다.

즉 local 결과는 방향성은 맞았지만, **실제 인프라 여유를 상당히 보수적으로 본 셈**에 가깝다.

## 4. 이번 단계에서 남겨둔 것

이번 단계에서 일부러 남겨둔 항목은 아래와 같다.

### 4.1 prod direct 추가 failure-side 구간

아직 확인하지 않은 것:

- `M1 50/50`

즉 mixed 실제 한계 위치는 `30/30과 40/40 사이`로 좁혀졌고,
그보다 더 높은 failure-side 구간은 아직 추가 확인하지 않았다.

### 4.2 automated Grafana capture

prod Grafana에서는 renderer가 일부 패널에서 `60s` timeout을 내고 있다.

따라서 이번 단계에서는:

- `capture.mode=manual`
- 수동 패널 확인만 수행
- showcase용 PNG 자동 생성은 보류

로 정리했다.

즉 이번 prod direct 1차는 **수치와 메타 중심 기록**으로 먼저 닫는다.

## 5. 지금 기준 판단

지금 기준 판단은 아래처럼 정리하는 것이 가장 자연스럽다.

- local에서 확보한 stable baseline은 실제 인프라에서도 유효했다.
- `R1 overview`의 실제 한계 구간은 local보다 더 뒤에 있었다.
- `M1 mixed`는 prod-direct에서 local보다 훨씬 높은 pair rate까지 버텼고, 실제 한계 위치도 `30/30과 40/40 사이`로 좁혀졌다.

즉 현재 단계는 "local 결과를 실제 인프라에서 1차 검증"을 넘어,
**overview read 한계 구간 일부와 mixed 추가 여유 구간까지 확인한 상태**로 보는 것이 맞다.

## 6. 다음 단계

다음 단계 후보는 두 갈래다.

### 6.1 prod direct 추가 확장

필요하면 아래를 추가 확인한다.

- `M1 50/50`
- 필요 시 `R2`, `R3`

이건 이미 확인한 mixed 한계 이후 더 높은 failure-side 구간을 참고용으로 보고 싶을 때 가는 단계다.

### 6.2 public 경유 최종 확인

또 다른 다음 단계는 public ingress를 포함한 최종 확인이다.

이 단계에서는:

- nginx / public route
- TLS
- 외부 네트워크 경로

까지 포함해 해석해야 한다.

즉 지금 문서 다음 질문은  
"local 안정 구간이 prod direct에서 재현되는가"에서  
"한계 구간까지 다시 볼 것인가, 아니면 public 경로 최종 확인으로 갈 것인가"로 옮겨간다.

## 7. 관련 문서

- 계획 문서:
  - [26-Prod-Direct-검증-계획.md](26-Prod-Direct-검증-계획.md)
- 다음 단계 계획:
  - [28-Prod-Public-검증-계획.md](28-Prod-Public-검증-계획.md)
- 실행 기록:
  - [04-대규모-부하-테스트-기록.md](04-대규모-부하-테스트-기록.md)
- 전체 종합:
  - [07-성능-개선-종합.md](07-성능-개선-종합.md)

## 결론

이번 prod direct 1차 검증의 결론은 아래 한 줄로 정리할 수 있다.

> prod-direct에서는 `W1 r100`, `R1 r10~30`, `M1 10/10~30/30`이 모두 안정 구간으로 확인됐고, `R1 overview` 한계 구간은 `30과 50 사이`, `M1 mixed` 한계 구간은 `30/30과 40/40 사이`로 올라갔다. 즉 local 병목 지도는 방향성은 맞았지만, 실제 인프라 여유는 더 크다.
