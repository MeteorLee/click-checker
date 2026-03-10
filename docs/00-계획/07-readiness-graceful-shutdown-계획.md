# 07. Readiness / Graceful Shutdown 계획 (v1.0)

## 목표
- 배포 중 애플리케이션이 갑자기 요청을 끊지 않도록 종료 정책을 정리한다.
- readiness 기준을 명확히 만들어 이후 Blue/Green 전환의 기반으로 사용한다.
- 종료/재기동 시 요청 손실을 줄이는 운영 기준을 코드와 설정에 반영한다.

---

# 7.0 고정 원칙

## 1. 종료 정책은 prod 기준으로 먼저 고정한다
- local 편의보다 prod 안정성을 우선한다.
- 개발 환경 변경은 최소화하고, 운영 경로에 필요한 설정부터 반영한다.

## 2. readiness와 liveness를 같은 의미로 쓰지 않는다
- readiness는 “트래픽을 받아도 되는 상태”를 판단한다.
- liveness는 “프로세스가 살아 있는가”를 판단한다.
- 이후 배포 전환은 readiness 기준으로 판단한다.

## 3. graceful shutdown은 앱 설정과 런타임 설정을 함께 본다
- Spring Boot 종료 정책만으로 끝내지 않는다.
- 컨테이너 종료 유예 시간과 healthcheck 정책도 같이 맞춘다.

## 4. 이번 범위는 기반 준비에 집중한다
- 무중단 전환 자체는 이번 범위가 아니다.
- Blue/Green, CodeDeploy, 관측 고도화는 후속 단계로 둔다.

---

# 7.1 범위 정의

## 포함(in scope)
- `application-prod.yml`의 graceful shutdown 설정
- readiness / liveness 노출 정책 점검 및 정리
- 종료 대기 시간 설정
- Docker compose 종료 유예 시간 점검
- 종료/재기동 시 기본 검증 절차 정리
- 관련 문서 / 실행기록 반영

## 제외(out of scope)
- Blue/Green 전환 구현
- CodeDeploy / S3 기반 배포
- 알림 / observability 실전 검증
- 대규모 부하 테스트

---

# 7.2 완료 기준 (Done)
- `server.shutdown=graceful`이 prod 설정에 반영된다.
- 종료 대기 시간이 명시적으로 설정된다.
- readiness 경로를 운영 판단 기준으로 설명할 수 있다.
- 앱 종료 시 즉시 프로세스가 끊기지 않도록 compose 종료 정책이 정리된다.
- 관련 검증 절차와 기록 문서가 남는다.

---

# 7.3 실행 순서

## 1) prod 애플리케이션 설정
1. `application-prod.yml` 점검
2. graceful shutdown 설정 반영
3. 종료 대기 시간 반영
4. readiness / health 노출 설정 점검

## 2) 런타임 종료 정책
1. Docker compose 종료 유예 시간 확인
2. 필요 시 prod 기준 종료 시간을 명시
3. healthcheck 기준과 readiness 사용 방식을 정리

## 3) 기본 검증
1. prod 기동 확인
2. readiness / health 응답 확인
3. 종료 시 로그 / 요청 처리 상태 확인
4. 관련 테스트 또는 수동 검증 절차 정리

## 4) 문서화
1. 실행기록 작성
2. 트러블슈팅이 있으면 별도 기록
3. 후속 Blue/Green 단계와 연결 포인트 정리

---

# 7.4 체크포인트

## 앱 설정
- `server.shutdown=graceful`
- 종료 대기 시간 설정
- readiness / health 설정이 prod 기준으로 설명 가능한지

## 런타임
- 컨테이너 종료 시 즉시 kill되지 않는지
- 종료 유예 시간과 앱 설정이 충돌하지 않는지

## 검증
- `/actuator/health` 응답 유지 여부
- readiness를 이후 전환 기준으로 사용할 수 있는지
- 종료 시 에러 스파이크나 요청 끊김이 과도하지 않은지

---

# 7.5 후속 작업
- 단일 EC2 Blue/Green 전환 구조 설계
- readiness 기반 트래픽 전환 절차 문서화
- graceful shutdown 실전 시나리오 검증
- observability 실전 검증과 연계
