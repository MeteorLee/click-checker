# 17. Readiness / Graceful Shutdown 종합

## 0. 목표
- 이후 Blue/Green 전환 전에 애플리케이션이 종료 시 요청을 급하게 끊지 않도록 기본 종료 정책을 정리한다.
- readiness / liveness 엔드포인트를 이후 전환 기준으로 사용할 수 있는 상태인지 확인한다.
- 배포 전달 수단보다 먼저 애플리케이션 자체의 교체 준비 상태를 갖춘다.

---

## 1. 이번에 반영한 내용

### 1.1 prod 애플리케이션 종료 정책
- `application-prod.yml`에 `server.shutdown: graceful` 추가
- `application-prod.yml`에 `spring.lifecycle.timeout-per-shutdown-phase: 30s` 추가

### 1.2 컨테이너 종료 유예 시간
- `docker-compose.prod.yml`의 `app` 서비스에 `stop_grace_period: 40s` 추가
- 애플리케이션 종료 대기 시간보다 컨테이너 종료 유예 시간을 더 길게 설정

### 1.3 readiness / liveness 확인
- app 컨테이너 내부에서 `/actuator/health/readiness` 확인
- app 컨테이너 내부에서 `/actuator/health/liveness` 확인

### 1.4 실제 종료 검증
- prod 설정 기준으로 app 컨테이너를 실제 중지
- 로그에서 graceful shutdown 시작/완료 메시지 확인

---

## 2. 확인 결과

### 2.1 readiness / liveness
- `/actuator/health/readiness` → `UP`
- `/actuator/health/liveness` → `UP`

### 2.2 graceful shutdown 로그
- `Commencing graceful shutdown. Waiting for active requests to complete`
- `Graceful shutdown complete`

### 2.3 해석
- prod 애플리케이션은 종료 시 즉시 끊지 않고, 활성 요청을 정리하려는 기본 정책을 가진 상태가 됐다.
- readiness / liveness 엔드포인트도 이후 전환 기준으로 사용할 수 있는 출발점이 마련됐다.

---

## 3. 현재 상태
- prod 설정에는 graceful shutdown 기본 정책이 반영돼 있다.
- 컨테이너 종료 유예 시간도 애플리케이션 종료 정책과 맞춰져 있다.
- readiness / liveness 엔드포인트가 실제로 응답한다.
- 아직 Blue/Green 전환 자체를 구현한 것은 아니며, 그 전 단계 기반 작업을 마친 상태다.

---

## 4. 왜 이 작업을 먼저 했는가
- CodeDeploy나 Blue/Green보다 먼저, 애플리케이션이 안전하게 내려갈 준비가 되어 있어야 한다.
- 종료 정책 없이 전환 구조만 먼저 만들면 요청 손실이나 강제 종료 문제가 숨겨진다.
- 즉, 이번 작업은 무중단 전환의 “배포 절차”보다 “애플리케이션 품질 기준”을 먼저 고정한 단계다.

---

## 5. 남은 후속 작업
- readiness를 실제 전환 기준으로 쓰는 Blue/Green 구조 설계
- 종료 중 요청 처리 시나리오를 조금 더 구체적으로 검증
- 이후 관측(Observability) 고도화와 연계
- 마지막에 S3 / CodeDeploy 기반 배포 전달 구조 검토

---

## 6. 결론
- 이번 작업으로 prod 애플리케이션은 readiness / liveness 엔드포인트와 graceful shutdown 기본 정책을 갖추게 됐다.
- 아직 무중단 배포 자체를 구현한 것은 아니지만, 그 단계로 넘어가기 전에 필요한 종료/전환 기반은 준비됐다.
- 포트폴리오 관점에서도 “배포 수단”보다 먼저 “서비스가 안전하게 교체될 준비”를 만든 단계로 설명할 수 있다.
