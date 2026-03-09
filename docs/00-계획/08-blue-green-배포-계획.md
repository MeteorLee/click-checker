# 08. Blue / Green 배포 계획 (v1.0)

## 목표
- 단일 EC2 내부에서 Blue / Green 두 애플리케이션 인스턴스를 번갈아 운영할 수 있는 구조를 만든다.
- Nginx upstream 전환으로 배포 중 서비스 중단을 줄인다.
- readiness 기준으로 새 인스턴스가 트래픽을 받을 준비가 됐는지 확인한 뒤 전환한다.

---

# 8.0 고정 원칙

## 1. 단일 EC2 안에서 해결한다
- 이번 범위는 멀티 인스턴스나 ALB 기반이 아니다.
- 한 대의 EC2 안에서 두 색상(Blue / Green)을 나누고 Nginx가 전환한다.

## 2. 앱 준비 상태는 readiness로 판단한다
- 단순 기동 여부가 아니라 readiness 응답이 `UP`인지 확인한다.
- 트래픽 전환 기준은 일반 health보다 readiness를 우선한다.

## 3. 전환은 Nginx upstream 교체로 한다
- 외부 사용자는 항상 같은 도메인으로 접근한다.
- 내부에서 어떤 색상이 응답할지는 Nginx가 결정한다.
- 현재 운영용 `nginx/click-checker.conf`는 기본 진입점 설정으로 유지한다.
- Blue / Green 전환 검증과 절차는 별도 전환용 nginx 설정 파일을 기준으로 다룬다.

## 4. 실패 시 즉시 이전 색상으로 돌아갈 수 있어야 한다
- 새 색상 기동 실패
- readiness 실패
- 전환 직후 이상 징후
가 보이면 기존 색상으로 바로 복귀할 수 있어야 한다.

## 5. 이번 범위는 “전환 구조”에 집중한다
- S3 / CodeDeploy 전달 구조는 이번 범위가 아니다.
- 배포 아티팩트 전달 고도화는 후속 단계로 둔다.

---

# 8.1 범위 정의

## 포함(in scope)
- Blue / Green 포트 전략 정의
- app 두 인스턴스 동시 기동 방식 정리
- Nginx upstream 전환 방식 정리
- 운영용 nginx와 전환용 nginx의 역할 분리
- readiness 기반 전환 절차 정의
- 실패 시 즉시 복구 절차 정의
- 관련 런북 / 종합 문서화

## 제외(out of scope)
- S3 / CodeDeploy
- 멀티 EC2 / ALB
- 오토스케일링
- 관측 / 알림 고도화

---

# 8.2 완료 기준 (Done)
- Blue / Green 포트가 명확히 정해진다.
- 두 앱 인스턴스를 동시에 띄울 수 있다.
- 새 색상의 readiness가 `UP`인 경우에만 전환한다.
- Nginx가 한 색상에서 다른 색상으로 전환할 수 있다.
- 전환 실패 시 이전 색상으로 즉시 복귀할 수 있다.
- 관련 운영 문서가 남는다.

---

# 8.3 실행 순서

## 1) 구조 설계
1. Blue / Green 포트 결정
2. 컨테이너 이름 / compose 구조 결정
3. Nginx upstream 파일 구조 결정

## 2) 애플리케이션 기동 구조
1. app-blue / app-green 동시 기동 가능하게 구성
2. 각 인스턴스의 readiness 확인 방식 정리
3. 종료 순서와 graceful shutdown 연결

## 3) Nginx 전환 구조
1. upstream 대상 정의
2. 운영용 nginx와 전환용 nginx 파일 역할 구분
3. 활성 색상 전환 방식 정의
4. 설정 반영 절차와 롤백 절차 정의

## 4) 검증
1. Blue 기동 → Green 기동
2. Green readiness 확인
3. Nginx 전환
4. 요청 흐름 확인
5. 이전 색상 종료

---

# 8.4 체크포인트

## 애플리케이션
- 두 인스턴스가 포트 충돌 없이 동시에 뜨는지
- readiness 경로가 색상별로 확인 가능한지
- graceful shutdown이 전환 절차와 충돌하지 않는지

## Nginx
- 외부 도메인은 그대로 유지되는지
- 내부 upstream만 바뀌는지
- 운영 기본 nginx와 전환용 nginx가 서로 덮어쓰지 않는지
- rollback 시 이전 색상으로 즉시 복구 가능한지

## 운영
- 전환 중 health/readiness 기준이 명확한지
- 장애 시 어느 단계에서 중단/복구할지 기준이 있는지

---

# 8.5 후속 작업
- 전환 결과를 관측으로 검증하는 단계로 연결
- 이후 S3 / CodeDeploy 기반 전달 구조와 결합
- 필요 시 무중단 전환 스크립트 자동화
