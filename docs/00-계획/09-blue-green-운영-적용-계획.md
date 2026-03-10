# 09. Blue/Green 운영 적용 계획 (v1.0)

## 목표

- 로컬에서 검증한 Blue/Green 구조를 실제 운영 환경에 올릴 수 있는 조건을 정리한다.
- 현재 안정적으로 운영 중인 `clickchecker.dev` / `grafana.clickchecker.dev` 경로를 깨뜨리지 않는 적용 순서를 정의한다.
- 운영 적용 전 체크리스트와 중단 기준을 먼저 고정한다.

---

# 9.0 고정 원칙

## 1. 현재 운영 nginx는 기본 진입점으로 유지한다
- 현재 운영 nginx 설정은 이미 HTTPS, Grafana Basic Auth, 도메인 연결까지 안정화된 상태다.
- 운영 적용 전까지는 이 설정을 기본 경로로 유지한다.

## 2. Blue/Green 전환은 단계적으로 올린다
- 한 번에 운영 nginx 전체를 Blue/Green 구조로 교체하지 않는다.
- 메인 앱 전환 경로만 조심스럽게 바꾼다.

## 3. 전환 기준은 readiness다
- 새 색상이 기동됐다는 사실만으로 전환하지 않는다.
- `/actuator/health/readiness`가 `UP`일 때만 전환한다.

## 4. 롤백은 즉시 가능해야 한다
- 전환 직후 응답 이상, readiness 하락, 5xx 증가가 보이면 이전 색상으로 즉시 되돌릴 수 있어야 한다.

## 5. Grafana 경로는 이번 단계에서 유지한다
- `grafana.clickchecker.dev`는 현재처럼 유지한다.
- 메인 앱 Blue/Green 적용이 관리자 경로를 깨뜨리지 않아야 한다.

## 6. `8080`은 한 번에 제거하지 않는다
- 현재 운영 메인 앱은 `127.0.0.1:8080`을 기준으로 안정적으로 동작 중이다.
- 운영 첫 적용에서는 `8080 -> 8081(blue)`로 먼저 옮긴다.
- 그 이후에야 `8081(blue) <-> 8082(green)` 교대 구조로 들어간다.

---

# 9.1 범위 정의

## 포함(in scope)
- 운영 적용 전 체크리스트 작성
- 운영 nginx와 Blue/Green 전환용 nginx의 차이 정리
- `8080`에서 `8081/8082` 체계로 옮기는 단계적 전환 전략 정의
- 운영 전환 절차 정의
- 운영 롤백 절차 정의
- 운영 검증 항목 정의

## 제외(out of scope)
- CodeDeploy / S3
- ALB / 멀티 EC2
- 완전 자동 무중단 배포
- Grafana 접근제어 추가 강화

---

# 9.2 완료 기준 (Done)

- 운영 적용 전 체크리스트가 있다.
- 운영 nginx에 어떤 부분만 바꿀지 명확하다.
- 전환 절차와 롤백 절차가 문서로 정리돼 있다.
- 운영 반영 후 확인할 항목이 정리돼 있다.

---

# 9.3 운영 적용 전 체크리스트

## 인프라
- EC2 리소스가 `app-blue`, `app-green` 동시 기동을 감당할 수 있는지 확인
- RDS 연결 상태 정상 확인
- `clickchecker.dev`, `grafana.clickchecker.dev` HTTPS 정상 확인

## 애플리케이션
- `app-blue`, `app-green` 모두 동일 이미지 기준으로 기동 가능
- `APP_COLOR` 응답으로 활성 색상 식별 가능
- readiness / liveness 응답 확인
- graceful shutdown 설정 반영 확인

## nginx
- 운영용 nginx 설정 백업
- 전환 시 수정할 upstream 위치 명확화
- Grafana HTTPS / Basic Auth 설정이 덮어써지지 않는지 확인

---

# 9.4 운영 적용 순서

## 0) 포트 전환 전략

운영 적용은 두 단계로 나눈다.

### 단계 A. legacy `8080`에서 blue `8081`로 이동
- 현재 운영 nginx upstream은 `127.0.0.1:8080`
- 첫 운영 적용에서는 `app-blue(8081)`를 새 기준으로 올린다.
- 이 단계에서는 `8080` 앱을 즉시 제거하지 않고 fallback으로 잠시 유지한다.

### 단계 B. `8081/8082` 교대 구조 정착
- `8081(blue)`가 안정화된 뒤에야 `8082(green)`까지 포함한 교대 구조로 들어간다.
- 이 시점부터 운영 nginx upstream은 `8081` 또는 `8082`만 바라본다.

## 1) 운영 nginx 백업
- 현재 `/etc/nginx/sites-available/default` 백업

## 2) 비활성 색상 기동
- 첫 운영 적용에서는 `app-blue`를 기동
- 이후 반복 적용에서는 현재 활성 색상 반대편 앱 기동
- readiness `UP` 확인

## 3) 운영 nginx의 메인 앱 upstream만 전환
- Grafana 블록은 유지
- 메인 앱 upstream만 다음 중 하나로 변경
  - 첫 운영 적용: `127.0.0.1:8081`
  - 이후 운영 적용: `127.0.0.1:8081` 또는 `127.0.0.1:8082`

## 4) nginx 검증 및 reload
- `nginx -t`
- `systemctl reload nginx`

## 5) 운영 도메인 검증
- `https://clickchecker.dev`
- `https://clickchecker.dev/actuator/health`
- 응답 `color` 확인

## 6) 이전 색상 종료
- 첫 운영 적용에서는 `8081(blue)` 안정화 후 `8080` 레거시 앱 종료 시점을 별도로 판단
- 이후 운영 적용에서는 이전 색상(`blue` 또는 `green`) 종료

---

# 9.5 롤백 기준

- 새 색상 readiness 실패
- 전환 직후 `clickchecker.dev` 응답 실패
- `color`가 기대와 다르거나 5xx 응답 발생
- Grafana 경로 이상 발생

위 조건 중 하나라도 충족하면:
- 운영 nginx를 직전 안정 upstream으로 되돌린다.
- `nginx -t` 후 reload한다.
- 필요 시 새 색상 컨테이너를 종료한다.

첫 운영 적용에서 문제가 생기면 기본 롤백 대상은 `127.0.0.1:8080` legacy 앱이다.

---

# 9.6 운영 적용 후 확인 항목

- `https://clickchecker.dev` 정상 응답
- `/actuator/health` 200
- 메인 루트 응답의 `color`가 새 색상
- `grafana.clickchecker.dev` 계속 정상
- 이전 색상 종료 후에도 서비스 정상

---

# 9.7 후속 작업

- 실제 운영 적용 결과를 런북 / 트러블슈팅 문서에 반영
- 필요 시 운영 전환 스크립트 분리
- 이후 CodeDeploy/S3 단계와 연결
