# 28. Prod Public 검증 계획

## 문서 목적

local 기준 결과와 `prod-direct` 확장 검증 결과 이후,  
실제 public 진입점 기준 최종 확인 계획을 한 문서로 정리한다.

이번 문서는 새 시나리오를 정의하는 문서가 아니라,
**왜 이제 public까지 보고, 무엇을 어떤 순서로 확인할지**를 한 번에 보게 만드는 실행 계획 문서다.

## 1. 왜 지금 prod public인가

지금까지는 아래 단계가 이미 정리됐다.

- local 기준 baseline과 한계 구간
- `prod-direct` 기준 stable baseline 재현
- `R1`, `M1`의 `prod-direct` 한계 구간 일부 확인

즉 다음 단계의 질문은
"EC2 / RDS direct 기준 결과가 실제 public 진입점에서도 비슷한가"에 가깝다.

따라서 지금 prod public은
**nginx / TLS / public route가 포함된 상태에서도 같은 안정 구간이 유지되는지 확인하는 최종 검증 단계**로 본다.

## 2. 이번 단계 검증 세트

이번 prod public 단계에서는 아래 세 개만 먼저 본다.

### 2.1 `W1 r100`

- 목적:
  - public write 대표 안정 구간 확인
- 이유:
  - local과 `prod-direct`에서 모두 안정 구간으로 재현된 write 대표 run이기 때문

### 2.2 `R1 r30`

- 목적:
  - public overview read 대표 상단 안정 구간 확인
- 이유:
  - `prod-direct`에서 `30 RPS`는 안정이고 `50 RPS`는 실패였으므로, public에선 먼저 `r30`이 적절하다.

### 2.3 `M1 30/30`

- 목적:
  - public mixed 대표 상단 안정 구간 확인
- 이유:
  - `prod-direct`에서 `30/30`은 안정이고 `40/40`은 실패였으므로, public 첫 확인은 `30/30`이 적절하다.

원칙:

- 이번 public 단계는 limit를 더 밀기보다 **public 진입점 오버헤드가 기존 안정 구간을 깨는지 확인하는 것**이 목적이다.
- `R1 r50`, `M1 40/40`은 필요 시 다음 단계에서 본다.

## 3. 실행 위치와 요청 대상

### 실행 위치

- 기본은 **EC2 내부 실행**

이유:

- 기존 perf 구조를 그대로 재사용하기 쉽다.
- dataset seed / snapshot restore가 계속 RDS direct를 사용하기 때문이다.
- public 확인에서 먼저 보고 싶은 것은 외부 사용자 전체 RTT가 아니라, **public ingress 계층 추가 영향**이다.

### 요청 대상

- direct port가 아니라 **public 도메인**
- 기본값:
  - `https://clickchecker.dev`

원칙:

- `PREPARE_BASE_URL`, `RUN_BASE_URL` 모두 public 도메인을 사용한다.
- public 검증에서는 현재 active color를 직접 고정하지 않는다.
- 대상 색상은 nginx upstream이 가리키는 현재 운영 target을 따른다.

## 4. 준비 체크리스트

실행 전 아래 항목을 먼저 확인한다.

- `https://clickchecker.dev` health / readiness 정상 확인
- 현재 nginx upstream 전환 직후 상태 안정 확인
- `scripts/perf/prod-public/...` 경로와 `artifacts/perf/prod-public/...` 경로 확인
- dataset seed / snapshot restore가 계속 RDS direct로 동작하는지 확인
- Grafana 캡처는 `manual` 기준으로 유지할지 확인

원칙:

- public 검증은 요청 경로만 public으로 바뀌는 것이고, dataset seed / restore는 계속 내부 DB direct를 사용한다.
- 이번 단계에서는 renderer timeout 이슈 때문에 scripted capture를 다시 열지 않는다.

## 5. 실행 순서

이번 단계 실행 순서는 아래로 고정한다.

1. `W1 r100`
2. `R1 r30`
3. `M1 30/30`

이유:

- 먼저 write 단독 public 오버헤드를 확인하고
- 그 다음 가장 무거운 read 단독 public 오버헤드를 확인하고
- 마지막에 mixed public 기준선을 확인하는 편이 해석이 쉽다.

즉 `prod-direct`에서 이미 안정으로 확인한 run을 **public 경로에서 다시 검증하는 순서**가 자연스럽다.

## 6. 기록 원칙

- [04-대규모-부하-테스트-기록.md](04-대규모-부하-테스트-기록.md)에 `environment=prod public`을 명시한다.
- `prod-direct` 결과와 나란히 비교할 수 있게 같은 시나리오 기준으로 기록한다.
- 숫자 자체보다 `public` 계층 추가 이후 지연 상승 폭과 안정성 변화를 먼저 본다.
- `showcase`는 이번 단계 종료 후 대표 run만 선별한다.

## 7. 해석 시 주의사항

- `prod-public`은 `prod-direct`와 달리 아래 요소가 추가된다.
  - nginx reverse proxy
  - TLS handshake / termination
  - public 도메인 경유
- 따라서 `prod-direct`보다 수치가 조금 나빠지는 것은 자연스러운 후보로 본다.
- 다만 그 차이가 안정 구간을 깨는 수준인지가 핵심이다.
- 이번 단계는 외부 지역 클라이언트 RTT까지 포함한 인터넷 사용자 경험 전체를 뜻하지는 않는다.

## 8. 관련 문서

- `prod-direct` 계획:
  - [26-Prod-Direct-검증-계획.md](26-Prod-Direct-검증-계획.md)
- `prod-direct` 종합:
  - [27-Prod-Direct-1차-종합.md](27-Prod-Direct-1차-종합.md)
- 공통 런북:
  - [03-대규모-부하-테스트-런북.md](03-대규모-부하-테스트-런북.md)
- 실행 기록:
  - [04-대규모-부하-테스트-기록.md](04-대규모-부하-테스트-기록.md)
- 전체 종합:
  - [07-성능-개선-종합.md](07-성능-개선-종합.md)

## 9. 다음 단계

이번 prod public 검증 세트를 마친 뒤에는 아래 순서로 간다.

- `prod-direct`와 `prod-public` 차이 비교
- public 경로에서도 유지된 안정 구간과 무너진 구간 정리
- 필요하면 구조 개선 우선순위 재판단

즉 이번 문서의 목적은
**직접 포트 기준 검증 다음에 public 경로를 어떤 순서로 확인할지 흐름을 고정하는 것**이다.

## 10. 실행 결과

이번 계획 문서 기준 첫 검증 세트는 실제로 모두 성공했다.

- `W1 r100`
- `R1 r30`
- `M1 30/30`

상세 결과와 해석은 [29-Prod-Public-1차-종합.md](29-Prod-Public-1차-종합.md)를 기준으로 본다.

## 결론

이번 단계에서 prod public은 새 시나리오가 아니라,  
이미 `prod-direct`에서 확인한 안정 구간을 실제 공개 진입점에서 다시 확인하기 위한 최종 검증 단계로 본다.

따라서 이번 문서의 결론은 다음 한 줄로 정리할 수 있다.

> prod public 첫 검증은 `W1 r100 -> R1 r30 -> M1 30/30` 순서의 최소 세트로 시작하는 것이 맞다.
