# 37. v2 공통 dataset 및 snapshot 설계

## 문서 목적

`W2 / R4 / R5 / R6 / M2`는 모두 같은 dataset 세계를 바라봐야 비교가 성립한다.  
이번 문서는 v2 현실형 부하 테스트에서 공통으로 재사용할 **dataset 구조와 snapshot restore 원칙**을 고정하는 데 목적이 있다.

이번 문서가 고정하는 범위는 아래와 같다.

- dataset version
- snapshot version
- tenant skew
- 총 이벤트 / 사용자 규모
- 시간 분포와 시간대 분포
- funnel / retention 표본 규칙
- snapshot restore 대상과 순서
- dataset 메타 정보

이번 문서는 아직 아래 항목까지 구현 수준으로 고정하지 않는다.

- 실제 seed 스크립트 구조
- 실제 snapshot 파일 경로와 파일명
- raw path와 canonical event type의 상세 상관관계
- prepare / run 구현 세부

## 공통 version

- dataset version:
  - `v2-dataset-v1`
- snapshot version:
  - `v2-dataset-v1-snap1`

## 공통 tenant 세계

- 총 `6개 organization`
- hot tenant `1개`
  - 전체 볼륨의 `50%`
- warm tenant `2개`
  - 전체 볼륨의 `30%`
- cold tenant `3개`
  - 전체 볼륨의 `20%`

즉 이 dataset은 `6개 org / 50-30-20` 모델을 그대로 반영한다.

## 공통 dataset 규모

### 전체 규모

- 총 이벤트:
  - `240,000`
- identified users:
  - `7,200`

### tenant별 분포

- hot tenant
  - 이벤트 `120,000`
  - identified users `3,600`
- warm tenant 각 1개
  - 이벤트 `36,000`
  - identified users `1,080`
- cold tenant 각 1개
  - 이벤트 `16,000`
  - identified users `480`

## payload / read 세계 일치 원칙

`W2`의 write payload 분포와 `R4 / R5 / R6 / M2`가 읽는 dataset 분포는 같은 세계를 바라보도록 맞춘다.

### identified / anonymous

- 전체 이벤트 기준:
  - `identified 70%`
  - `anonymous 30%`

### identified 내부 existing / new

- identified 내부:
  - `existing 80%`
  - `new 20%`

즉 전체 이벤트 기준 해석은 아래와 같다.

- existing identified:
  - `56%`
- new identified:
  - `14%`
- anonymous:
  - `30%`

### canonical event type 분포

- `view 50%`
- `click 30%`
- `signup 12%`
- `purchase 8%`

### path 그룹 분포

- browse:
  - `60%`
- product / analysis:
  - `25%`
- conversion:
  - `15%`

### tenant별 payload 차이

- tenant 간 차이는 우선 **volume skew만** 준다.
- payload 분포는 tenant 공통으로 시작한다.

## 시간 분포

dataset 전체 기간은 `90일`이다.

### 기간 분포

- 최근 `7일`
  - `50%`
- `8~30일`
  - `30%`
- `31~90일`
  - `20%`

### 시간대 분포

- `09:00~18:00`
  - `60%`
- `18:00~24:00`
  - `25%`
- `00:00~09:00`
  - `15%`

## funnel 표본 규칙

dataset은 `funnels/report`가 실제 전환과 이탈을 함께 보여줄 수 있도록 아래 표본을 포함한다.

### 성공 체인

- `view + routeKey=pricing`
- `signup`
- `purchase`

이 체인은 `conversionWindowDays = 7` 안에 들어오도록 만든다.

### 실패 체인

- `view + routeKey=pricing -> signup -> drop`
- `view -> click -> drop`

즉 funnel은 성공 전환만 있는 깨끗한 데이터가 아니라 drop-off가 함께 존재하는 데이터로 만든다.

## retention 표본 규칙

dataset은 `retention/daily`, `retention/matrix`가 의미 있게 계산되도록 아래 재방문 표본을 포함한다.

- day 1 재방문
- day 7 재방문
- day 14 재방문
- day 30 재방문

## snapshot / reset 전략

### 기본 원칙

- snapshot은 `org-scoped logical snapshot`으로 관리한다.
- 복원 방식은 `SQL replay`를 사용한다.
- strict baseline run은 매 run 시작 전에 full restore 한다.
- quick reset은 6개 org 전체를 범위로 하되, 배경 dataset은 유지하고 이전 run overlay만 삭제한다.

여기서 `snapshot.sql`은 전통적인 raw dump가 아니라, **deterministic rule로 동일한 논리 상태를 다시 만드는 logical snapshot replay script**로 본다.

즉 이 snapshot은 아래를 보장한다.

- 같은 org 6개
- 같은 tenant weight
- 같은 total events / users
- 같은 시간 분포
- 같은 event type / path 분포
- 같은 funnel / retention 표본 규칙

반면 아래는 현재 단계에서 보장 대상이 아니다.

- PK 값 완전 동일성
- sequence 값 완전 동일성

즉 v2는 **물리 상태 동일성**보다 **논리 상태 동일성**을 기준으로 한다.

### full restore 대상 테이블

- `route_templates`
- `event_type_mappings`
- `users`
- `events`

### full restore 삭제 순서

- `events`
- `users`
- `route_templates`
- `event_type_mappings`

### full restore 삽입 순서

- `route_templates`
- `event_type_mappings`
- `users`
- `events`

### reset mode

- `full`
  - logical snapshot replay
  - baseline / 문서용 기준 run / dataset 이상 의심 시 사용
- `quick`
  - baseline org, baseline users, route templates, event type mappings, 배경 events는 유지
  - `k6-v2` run이 추가한 overlay event만 삭제
  - 신규 user overlay는 누적을 허용하고, strict reset이 필요할 때만 `full` restore로 되돌린다
- `skip`
  - read-only 재실행처럼 상태 오염이 없다고 판단한 경우 reset을 생략

## payload OID 전환 후속 메모

- `V15__events_payload_oid_to_text.sql`로 `events.payload`는 `OID -> TEXT` 전환을 완료했다.
- 기존 large object 정리는 같은 migration 안에서 처리하지 않고 분리했다.
  - 이유:
    - inline `lo_unlink()`가 `out of shared memory / max_locks_per_transaction`로 실패했기 때문
- 따라서 old large object cleanup은 후속 maintenance 성격의 `V16` draft로 따로 관리한다.
- 초안 위치:
  - [V16__cleanup_orphan_large_objects.sql](/home/ghtmd/projects/click-checker/src/main/resources/db/migration-drafts/V16__cleanup_orphan_large_objects.sql)

현재 기본값:

- `W2`
  - `quick`
- `R4 / R5 / R6`
  - `skip`
- `M2`
  - `quick`

추가 원칙:

- reset mode는 scenario 기본값만으로 끝나지 않는다.
- dataset state가 dirty이고 다음 scenario 기본값이 `skip`이면, prepare는 자동으로 `quick`을 선택할 수 있다.
- 예:
  - `W2` 또는 `M2` run 이후의 `R4 / R5 / R6`

## restore 이후 sanity check

`full` 또는 `quick` reset 뒤에는 최소 아래 항목을 검증한다.

- organization 수:
  - `6`
- total events:
  - `240,000`
- total identified users:
  - `full`에서는 `7,200`
  - `quick`에서는 baseline 이상이면 통과
- hot / warm / cold 분포가 메타와 대체로 일치하는지
- 대표 route template 존재 여부
  - 예:
    - `pricing`
    - `checkout`
- 대표 event type mapping 존재 여부
  - 예:
    - `page_view -> view`
    - `purchase_complete -> purchase`

즉 reset은 SQL replay나 quick cleanup만 성공했다고 끝나는 것이 아니라, **dataset 계약이 실제로 지켜졌는지**까지 확인해야 완료로 본다.

## dataset 메타 정보

dataset / snapshot과 함께 아래 메타 정보를 기록한다.

- dataset version
- snapshot version
- total events
- total identified users
- tenant 분포
- event type 분포
- path 그룹 분포
- 기간 분포
- 시간대 분포
- funnel 표본 규칙
- retention 표본 규칙
- seed 값
- created_at
- schema fingerprint 또는 app commit

## 파일별 책임 분해

### `scripts/perf/common/v2-dataset-lib.sh`

역할:

- dataset 존재 확인
- seed / restore 결정
- dataset meta 읽기 / 쓰기
- 공통 상수와 메타 schema 관리
- sanity check orchestration

권장 함수:

- `ensure_v2_dataset`
- `seed_v2_dataset`
- `restore_v2_snapshot`
- `write_v2_dataset_meta`
- `load_v2_dataset_meta`
- `verify_v2_dataset`

### `scripts/perf/common/v2-dataset-sql.sh`

역할:

- replay SQL 생성
- DELETE / INSERT SQL 조립
- 공통 데이터 규칙을 SQL에 주입

권장 함수:

- `generate_v2_snapshot_sql`
- `apply_v2_snapshot_sql`

### `scripts/perf/local/common/v2-seed-dataset.sh`

역할:

- local postgres adapter
- local 환경에서 dataset seed 실행
- 필요 시 local용 organization / API key 생성 연계

### `scripts/perf/local/common/v2-restore-snapshot.sh`

역할:

- local postgres에 snapshot replay
- restore 후 sanity check

### `scripts/perf/prod-direct/common/v2-seed-dataset.sh`

역할:

- prod-direct RDS adapter
- `db-lib.sh`를 통한 dataset seed 실행

### `scripts/perf/prod-direct/common/v2-restore-snapshot.sh`

역할:

- prod-direct RDS에 snapshot replay
- restore 후 sanity check

## prepare 흐름

각 시나리오의 prepare는 dataset을 직접 만들지 않고, 아래 흐름으로 공통 dataset을 보장받는다.

1. DB 연결 확인
2. schema / version 확인
3. `ensure_v2_dataset`
4. dataset meta 검증
5. seed 또는 restore 실행
6. post-restore sanity check
7. run meta 생성

즉 시나리오 레이어는 **세계 생성 책임**을 가지지 않고, 공통 dataset 계약을 만족하는지 확인만 한다.

## 이번 문서의 결론

> v2는 `6개 org / 240k events / 7,200 identified users / 90일 분포 / realistic skew / funnel + retention 표본 / SQL replay snapshot restore`를 갖는 공통 dataset 세계를 기준으로 실행한다.
