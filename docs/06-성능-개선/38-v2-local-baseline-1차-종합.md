# 38. v2 local baseline 1차 종합

## 문서 목적

`W2 / R4 / R5 / R6 / M2` 기반 현실형 부하테스트 v2를 처음 local에서 실제 duration으로 실행한 결과를 한 문서로 묶는다.

이번 문서의 목적은 개별 run 로그를 반복해서 나열하는 것이 아니라,

- v2 realistic read가 얼마나 안정적인지
- v2 realistic write의 시작선을 어디로 봐야 하는지
- realistic mixed가 벌써 어느 정도 압박을 주는지

를 한 번에 정리하는 데 있다.

## 실행 범위

이번 1차 local baseline에서 확인한 run은 아래와 같다.

- `W2 s1`
- `R4 v1 s1`
- `R5 v1 s1`
- `M2 s1`
- `W2 60 RPS` 재보정 probe
- `W2 80 RPS` 재보정 probe
- `R6 s1`

## 결과 요약

| 시나리오 | runId | 결과 | 핵심 수치 | 해석 |
| --- | --- | --- | --- | --- |
| `W2 s1` | `w2-20260322-100328-s1-baseline` | `threshold_fail` | `p95 16.91s`, fail `2.08%`, achieved `~63 RPS`, dropped `10543` | 기존 `100 RPS` stage 1은 realistic write baseline이 아니라 limit probe에 가까웠다 |
| `R4 v1 s1` | `r4-20260322-101011-s1-v1-baseline` | `success` | `p95 53.93ms`, `p99 83.78ms`, fail `0%` | 집계 read mix baseline은 충분히 안정적이다 |
| `R5 v1 s1` | `r5-20260322-102232-s1-v1-baseline` | `success` | `p95 270.67ms`, `p99 435.35ms`, fail `0%` | 분석 read mix baseline도 안정 구간이다 |
| `M2 s1` | `m2-20260322-103144-s1-baseline` | `success` | read `p95 1.81s`, write `p95 1.41s`, fail `0%`, dropped `165` | threshold는 통과했지만 realistic mixed는 이미 약간 빡빡하다 |
| `W2 60 probe` | `w2-20260322-105941-r60-probe` | `success` | `p95 187.82ms`, `p99 446.73ms`, fail `0%`, dropped `0` | 새 baseline 후보 `60 RPS`가 안정적으로 통과했다 |
| `W2 80 probe` | `w2-20260322-114415-r80-probe` | `success` | `p95 83.2ms`, `p99 457.79ms`, fail `0%`, dropped `0` | `80 RPS`도 stable이며 stretch stage로 쓰기 충분하다 |
| `R6 s1` | `r6-20260322-120522-s1-baseline` | `success` | `p95 183.12ms`, `p99 295.76ms`, fail `0%` | `R4 + R5` combined read도 baseline으로 충분히 안정적이다 |

## 핵심 해석

### 1. 현실형 read는 생각보다 이미 안정적이다

- `R4 v1 s1`
- `R5 v1 s1`
- `R6 s1`

셋 모두 threshold를 충분히 여유 있게 통과했다.

즉 v2의 첫 local baseline에서는 read mix 자체가 먼저 무너지기보다,
현실형 write 쪽 시작선을 다시 잡는 일이 더 우선이라는 점이 드러났다.

### 2. `W2`의 기존 stage 1은 너무 높았다

첫 `W2 s1`은 문서상 정의대로 `100 RPS`로 시작했지만,
실제론 baseline이 아니라 거의 limit probe처럼 동작했다.

- `p95 16.91s`
- 실패율 `2.08%`
- achieved throughput `~63 RPS`

즉 `100 RPS`를 realistic write baseline으로 두는 건 맞지 않았다.

### 3. `W2` 시작선은 `60`이 더 자연스럽다

같은 dataset / same warm state 기준으로 다시 본 결과:

- `60 RPS`는 안정적으로 통과
- `80 RPS`도 안정적으로 통과
- `100 RPS`는 실패

따라서 현재 local v2 기준 write ladder는 아래처럼 읽는 게 가장 자연스럽다.

- stage 1: `60`
- stage 2: `80`
- stage 3: `100`

### 4. realistic mixed는 첫 단계부터 이미 의미가 있다

`M2 s1`은 threshold는 통과했지만 dropped가 있었다.

이건 곧:

- 현실형 read만 따로 보면 안정적이지만
- 현실형 write와 같이 붙으면
- shared resource 경쟁이 벌써 보인다는 뜻이다.

즉 `M2`는 아직 실패는 아니지만,
이미 구조 개선 우선순위를 읽기에 충분한 시나리오가 되었다.

## 이번 1차 baseline으로 고정할 것

- `R4 v1 s1` local baseline
- `R5 v1 s1` local baseline
- `R6 s1` local baseline
- `W2` ladder `60 / 80 / 100`

`M2 s1`은 baseline으로 쓸 수는 있지만,
완전 stable이라기보다 **통과한 degraded baseline**에 더 가깝다고 보는 편이 맞다.

## 다음 단계

1. 재조정한 `W2 60 / 80 / 100` 기준으로 realistic write stage별 해석을 확정한다
2. `M2` stage 2 이상으로 올릴 때 realistic mixed가 어디서부터 limit 쪽으로 들어가는지 확인한다
3. `W2 / R4 / R5 / R6 / M2`를 함께 읽어
   - realistic 구조 개선 우선순위를 다시 정한다
4. 필요 시 stage 2 / stage 3와 heavy preset으로 확장한다

## 후속 개선 검증 메모

baseline 확보 이후 local에서 두 가지 후속 검증을 추가로 진행했다.

- local Hikari pool 확장
  - `maximum-pool-size=40` 적용 뒤 `M2 s2`를 다시 봤지만 throughput `120.47 req/s`, read `p95 12.55s`, write `p95 11.74s`, dropped `22540`로 거의 움직이지 않았다.
  - 즉 local mixed 포화의 주원인을 pool 기본값으로 보긴 어렵다.
- `EventUser` select-then-insert 제거
  - `users (organization_id, external_user_id)` 유니크 키를 활용한 native upsert로 user id를 확보하도록 변경했다.
  - `M2 s2`는 여전히 fail이지만 throughput이 `136.11 req/s`로 오르고 read/write `p95`가 `9~10s`대로 내려왔다.
  - `W2 100 RPS`는 같은 변경 뒤 `p95 176.1ms`, fail `0%`로 성공 구간으로 이동했다.
- `W2` 상단 probe
  - 같은 변경 뒤 `120 RPS`는 `p95 1.41s`, fail `0%`, achieved `~116 RPS`, dropped `105`로 통과했다.
  - `140 RPS`도 `p95 2.12s`, fail `0%`, achieved `~136 RPS`, dropped `59`로 통과했다.
  - `160 RPS`도 `p95 1.42s`, fail `0%`, achieved `~155 RPS`, dropped `22`로 통과했다.
  - `200 RPS`도 `p95 989.51ms`, fail `0%`, achieved `~191 RPS`, dropped `660`으로 threshold 내 통과했다.
  - 반면 `250 RPS`는 `p95 7.61s`, `p99 8.81s`, dropped `4606`으로 threshold fail,
  - `300 RPS`는 `p95 11.02s`, `p99 13.26s`, dropped `15789`로 명확한 failure band였다.
  - 즉 write-only 기준 ceiling은 이제 `200과 250 사이`로 보는 것이 맞다.

즉 이번 후속 검증의 결론은 아래와 같다.

- `EventUser` 경로는 실제 write 병목이 맞다.
- 그러나 realistic mixed를 stage 2에서 통과시키기엔 아직 부족하다.
- write-only 상한은 `200과 250 사이`까지 크게 올라갔으므로, 이제 `W2`보다는 `M2`가 다음 구조 개선 우선순위를 결정하는 지표에 더 가깝다.
- 다음 1순위는 payload 저장 방식보다 집계/분석 쿼리 본체와 mixed shared DB work를 더 직접적으로 줄이는 쪽이다.

## quick reset 전략 재조정 메모

후속으로 `payload OID -> TEXT` 전환을 적용했고, quick reset 전략도 다시 정리했다.

- 기존 quick reset:
  - overlay event 삭제
  - overlay new user 삭제
- 변경 후 quick reset:
  - overlay event만 삭제
  - overlay new user는 누적 허용
  - strict baseline / 기록용 run만 full restore

이 변경의 목적은 `users DELETE`가 FK 확인 때문에 과도하게 느려지는 문제를 피하는 데 있다.

### 변경 후 결과

- `W2 quick prepare`
  - 다시 정상 통과
- `M2 s2`
  - prepare는 정상화
  - main run은 여전히 threshold fail
  - runId:
    - `m2-20260322-185539-s2-after-event-only-quick-reset`
  - read:
    - `p95 10.46s`
    - `p99 24.36s`
  - write:
    - `p95 8.47s`
    - `p99 10.07s`
  - achieved throughput:
    - `166.35 req/s`
  - dropped:
    - `8572`

즉 quick reset 전략 변경은 **반복 실험 생산성 개선**에는 성공했지만, `M2 s2` 본체 병목은 그대로 남아 있다.

## resolver metadata cache 검증 메모

후속으로 Spring Cache + Caffeine 기반 resolver metadata cache를 추가했다.

- 캐시 대상:
  - 조직별 active route templates
  - 조직별 active event type mappings
- 의도:
  - 집계/분석 요청마다 반복되던 작은 메타 조회를 메모리에서 재사용

### 캐시 적용 결과

- cold cache 첫 run
  - runId:
    - `m2-20260322-203204-s2-after-caffeine-cache`
  - read:
    - `p95 17.10s`
    - `p99 43.17s`
  - write:
    - `p95 14.29s`
    - `p99 16.39s`
  - achieved throughput:
    - `121.64 req/s`
  - dropped:
    - `21999`
- steady-state rerun
  - runId:
    - `m2-20260322-204135-s2-rerun-after-caffeine`
  - read:
    - `p95 7.51s`
    - `p99 8.90s`
  - write:
    - `p95 7.19s`
    - `p99 8.34s`
  - achieved throughput:
    - `164.40 req/s`
  - dropped:
    - `8376`

### 해석

- 앱 재기동 직후 첫 run은 cold cache penalty가 커서 오히려 더 나빴다.
- rerun은 event-only quick reset 기준 `M2 s2`보다 read/write `p95`가 내려가고 dropped도 소폭 줄었다.
- 즉 resolver metadata cache는 **steady-state mixed 비용을 일부 줄이는 데는 의미가 있었지만, `M2 s2`를 threshold 안으로 넣을 만큼 크진 않았다.**
- 따라서 다음 우선순위는 resolver 메타 조회보다 집계/분석 쿼리 본체와 shared DB work를 더 직접적으로 줄이는 쪽이다.

## time-series DB bucket 검증 메모

후속으로 time-series/aggregate read에서 raw timestamp를 많이 가져와 JVM에서 다시 묶던 구조를,
Postgres가 처음부터 `HOUR/DAY` bucket으로 집계하는 구조로 바꿨다.

### 변경 후 결과

- `R4 v2 s2`
  - runId:
    - `r4-20260322-231906-s2-v2-after-db-bucket`
  - 결과:
    - `success`
  - read:
    - `p95 57.2ms`
    - `p99 136.82ms`
  - 실패율:
    - `0%`
  - dropped:
    - 없음

- `M2 s2`
  - runId:
    - `m2-20260322-232712-s2-after-db-bucket`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 8.13s`
    - `p99 9.85s`
  - write:
    - `p95 7.51s`
    - `p99 9.03s`
  - achieved throughput:
    - `169.76 req/s`
  - dropped:
    - `6843`

### 해석

- `R4 v2 s2`는 local stage 2에서도 매우 안정적으로 동작했다.
- `M2 s2`는 여전히 fail이지만, event-only quick reset 기준 이전 결과보다
  - throughput 증가
  - read/write `p95` 감소
  - dropped 감소
가 확인됐다.
- 즉 time-series DB bucket은 **read-only에는 크게 먹히고, realistic mixed에도 분명한 개선을 주지만 아직 마지막 병목을 닫을 정도는 아니다.**

## overview summary 단일 쿼리 검증 메모

후속으로 activity overview의 current summary 3개와 previous total을 각각 따로 읽던 구조를,
native 단일 쿼리로 통합했다.

### 변경 후 결과

- 첫 run
  - runId:
    - `m2-20260323-112733-s2-after-overview-summary-native`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 13.48s`
    - `p99 16.27s`
  - write:
    - `p95 12.73s`
    - `p99 15.85s`
  - achieved throughput:
    - `117.80 req/s`
  - dropped:
    - `22969`

- steady-state rerun
  - runId:
    - `m2-20260323-113518-s2-after-overview-summary-native-rerun`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 8.08s`
    - `p99 10.16s`
  - write:
    - `p95 7.30s`
    - `p99 9.65s`
  - achieved throughput:
    - `173.11 req/s`
  - dropped:
    - `6009`

### 해석

- 앱 재기동 직후 첫 run은 cold-start 성격이 강해서, 이 변경을 오히려 나쁘게 보이게 만들었다.
- 같은 앱 인스턴스에서 rerun하면 이전 best였던 time-series DB bucket 기준 결과보다
  - throughput 증가
  - read/write `p95` 감소
  - dropped 감소
가 모두 확인됐다.
- 즉 overview summary 단일 쿼리는 **steady-state mixed 기준으로는 keep할 가치가 있는 소폭 개선**으로 보는 편이 맞다.
- 다만 threshold 자체를 넘기지는 못했으므로, 다음 병목은 여전히 aggregate/unique-user와 shared DB work 쪽에 더 가깝다.

## unique-user raw pair 경량화 검증 메모

후속으로 route/event-type unique-user 집계에서 불필요한 `count(*)`와 count 기반 정렬을 제거하고,
DB가 distinct `(rawKey, eventUserId)` pair만 반환하도록 줄였다.

### 변경 후 결과

- 첫 run
  - runId:
    - `m2-20260323-122707-s2-after-unique-user-pairs`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 13.91s`
    - `p99 16.75s`
  - write:
    - `p95 12.57s`
    - `p99 15.51s`
  - achieved throughput:
    - `133.61 req/s`
  - dropped:
    - `18260`

- steady-state rerun
  - runId:
    - `m2-20260323-123609-s2-after-unique-user-pairs-rerun`
  - 결과:
    - `success`
  - read:
    - `p95 4.63s`
    - `p99 6.08s`
  - write:
    - `p95 4.30s`
    - `p99 5.87s`
  - achieved throughput:
    - `188.76 req/s`
  - dropped:
    - `977`

### 해석

- 앱 재기동 직후 첫 run은 again cold-start outlier 성격이 강했다.
- 같은 앱 인스턴스에서 rerun하면 기존 best였던 overview summary 단일 쿼리 기준 결과보다
  - throughput 증가
  - read/write `p95` 대폭 감소
  - dropped 대폭 감소
가 모두 확인됐다.
- 특히 steady-state rerun은 `M2 s2`에서 처음으로 read/write threshold를 모두 통과했다.
- 즉 unique-user raw pair 경량화는 **aggregate/unique-user 계열의 불필요한 raw group-by 비용이 실제 mixed 병목 일부였음을 보여준 첫 직접 근거**다.

## aggregate regrouping raw count 정렬 제거 검증 메모

후속으로 route/canonical로 다시 합칠 raw count 쿼리에서 DB 정렬을 제거하고,
aggregate 서비스가 최종 결과만 정렬하도록 경로를 분리했다.

의도는 아래와 같았다.

- raw regrouping 내부에서는 DB가 중간 결과를 굳이 정렬할 필요가 없고
- 앱이 route/canonical로 합친 뒤 최종 top N만 다시 정렬하므로
- 중간 sort 비용을 줄이면 mixed가 조금 더 가벼워질 수 있다는 가설

### 변경 후 결과

- 첫 run
  - runId:
    - `m2-20260323-125814-s2-after-aggregate-regroup-order-removal`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 6.02s`
    - `p99 7.12s`
  - write:
    - `p95 5.64s`
    - `p99 6.82s`
  - achieved throughput:
    - `187.35 req/s`
  - dropped:
    - `2047`

- rerun
  - runId:
    - `m2-20260323-131125-s2-after-aggregate-regroup-order-removal-rerun`
  - 결과:
    - `threshold_fail`
  - read:
    - `p95 9.12s`
    - `p99 11.39s`
  - write:
    - `p95 8.30s`
    - `p99 10.76s`
  - achieved throughput:
    - `164.27 req/s`
  - dropped:
    - `8784`

### 해석

- 첫 run만 보면 수치가 아주 나쁘진 않았지만, unique-user raw pair 경량화 기준 best를 넘진 못했다.
- rerun은 오히려 더 나빠져 steady-state 개선으로 보기도 어려웠다.
- 로컬 Postgres `EXPLAIN ANALYZE`로 실제 ordered/unordered 쿼리를 비교해보면
  - `path` 집계
  - `event_type` 집계
  - `path + event_type` 집계
  모두 ordered/unordered 차이가 수 ms 수준이었다.
- 즉 이 변경은 코드 정리성은 있지만, realistic mixed를 좌우하는 큰 레버는 아니었다.

결론적으로 이 시도는

- **구조 정리 관점에서는 의미가 있지만**
- **`M2 s2`를 더 안정적으로 통과시키는 직접적인 perf win으로 채택하기엔 근거가 약하다**

로 정리하는 편이 맞다.

## overview 응답 캐시 + `2m` warm-up 검증 메모

후속으로 overview 응답을 `5분` TTL로 캐시했고, app restart 직후 first-run 편차를 줄이기 위해 `M2 s2` warm-up을 `2m`로 늘려 다시 확인했다.

### 변경 후 결과

- run
  - runId:
    - `m2-20260323-135843-s2-after-overview-cache-warm2m`
  - 결과:
    - `success`
  - read:
    - `p95 1.24s`
    - `p99 2.27s`
  - write:
    - `p95 1.11s`
    - `p99 1.99s`
  - achieved throughput:
    - `193.94 req/s`
  - dropped:
    - `29`

### 해석

- 이번 결과는 local `M2 s2` 기준으로 가장 안정적인 통과 구간이다.
- overview 응답 캐시는 keep할 가치가 있고, 동시에 app restart 직후 first-run 결과를 그대로 steady-state 결론으로 읽으면 안 된다는 점도 더 분명해졌다.
- 즉 `M2`는 코드 개선만이 아니라 **warm-up 프로토콜**에도 민감하다.
- 현재 local 기준 가장 보수적인 해석은 아래와 같다.
  - steady-state 비교용 표준 run:
    - 기존 `30s / 5m / 0`
  - app restart 직후 cold-start 편차 제거용 확인:
    - sacrificial warm run 1회
    - 또는 `2m` warm-up run

이 메모는 `M2` 기본 ladder를 바꾸기보다, realistic mixed 결과를 해석할 때 **cold-start와 steady-state를 분리해 읽어야 한다는 규칙**을 남기기 위한 것이다.

## 결론

> v2 local baseline 1차 결과는 **현실형 read는 이미 안정적이고, 현실형 write의 시작선은 `100`이 아니라 `60` 근처**라는 점을 보여줬다. 또한 realistic mixed는 첫 단계부터 이미 약간 빡빡한 신호를 주기 시작했다.

후속 검증까지 포함하면 결론은 한 단계 더 바뀐다.

> `EventUser` 경로 최적화, payload text 전환, resolver metadata cache, overview summary 단일 쿼리, unique-user raw pair 경량화까지 적용한 뒤 steady-state `M2 s2`는 threshold를 통과했고, overview 응답 캐시 + `2m` warm-up 확인에서는 local first-run도 매우 안정적으로 통과했다. 이후 aggregate regrouping raw count 정렬 제거도 시도했지만 perf win 근거는 약했다. 따라서 현재 realistic mixed의 다음 우선순위는 “통과 여부”보다 **여유 구간 확대와 cold-start 편차 축소**, 그리고 더 큰 폭의 shared DB work 절감 쪽에 더 가깝다.
