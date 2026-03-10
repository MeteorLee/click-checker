# 21. Blue/Green 운영 적용 종합

## 0. 목적
- Blue/Green 운영 적용 작업의 결과를 한 문서에서 빠르게 확인한다.
- 어떤 변경이 반영됐는지, 실제 운영에서 무엇이 검증됐는지, 현재 기준 색상이 무엇인지 정리한다.

---

## 1. 이번 적용의 목표
- 단일 `app(8080)` 중심 운영 구조에서 `app-blue(8081)` / `app-green(8082)` 교대 구조로 전환한다.
- 전환 시점마다 readiness와 실제 도메인 응답을 함께 확인해 안전하게 전환한다.
- 전환 실패 시 이전 색상으로 즉시 복구 가능한 운영 절차를 확보한다.

---

## 2. 이번에 반영한 내용

### 2.1 운영 실행 구조
- `docker-compose.prod.yml`에 `app-blue`, `app-green` 서비스를 분리했다.
- 각 서비스는 `APP_COLOR`, `SERVER_PORT`, readiness healthcheck를 가진다.
- legacy `app` 서비스는 기본 운영 경로에서 제외하고, 필요 시에만 쓰도록 분리했다.

### 2.2 nginx 전환 구조
- 메인 앱 라우팅을 upstream(`click_checker_app`) 기준으로 정리했다.
- Blue/Green 전환 시 nginx가 `8081` 또는 `8082` 중 하나만 바라보도록 단순화했다.
- Grafana(`grafana.clickchecker.dev`) 설정은 앱 전환과 분리해 유지했다.

### 2.3 전환 스크립트
- 운영 전환용 스크립트 `scripts/blue-green-prod-switch.sh`를 추가했다.
- 핵심 흐름:
  - 대상 색상 기동
  - readiness 확인
  - 직접 응답 확인(`color`)
  - nginx 전환 + reload
  - 공개 도메인 재검증
  - 성공 시 이전 색상 정리

### 2.4 배포 파이프라인 정리
- GitHub Actions의 인라인 대형 스크립트 실행 방식을 분리형(`scripts/deploy-prod-blue-green.sh`)으로 바꿨다.
- SSH 액션 내부 파싱 오류(`syntax error near unexpected token ';'`)를 제거했다.
- 배포 검증이 전환 이후 단계에서 실패하면 이전 색상으로 자동 복구하도록 보강했다.

---

## 3. 운영 적용 결과

### 3.1 실제 전환 이력
- 1차: `8080(legacy) -> 8081(app-blue)`
- 2차: `8081(app-blue) -> 8082(app-green)`

### 3.2 현재 활성 상태
- 현재 메인 앱 활성 색상: `green`
- 현재 메인 앱 포트: `8082`
- `https://clickchecker.dev` 응답에서 `color=green` 확인
- `/actuator/health` 정상 응답 확인

### 3.3 관측/관리 경로 상태
- 메인 서비스: `https://clickchecker.dev`
- Grafana: `https://grafana.clickchecker.dev` (Basic Auth + Grafana 로그인)
- 앱 전환 중에도 Grafana 경로는 영향 없이 유지

---

## 4. 검증 포인트
- 대상 색상 readiness `UP`
- 대상 색상 직접 루트 응답에서 기대 `color` 확인
- nginx 전환 후 공개 도메인 응답 정상
- 공개 도메인 응답의 `color`가 목표 색상과 일치
- 이상 시 이전 색상으로 복구 가능

---

## 5. 이번 작업에서 얻은 결론
- 현재 구조는 단일 컨테이너 재기동 방식보다 운영 안정성이 높다.
- 전환 검증 기준을 `readiness + 실제 도메인 응답`으로 잡은 것이 핵심이다.
- 배포 스크립트 분리로 CI 파싱 이슈를 줄였고, 운영 재현성도 높아졌다.

---

## 6. 다음 운영 기준
- 다음 교대 전환 기준: `green(8082) -> blue(8081)`
- 전환은 운영 스크립트 기준으로 수행하고, 수동 편집은 장애 복구 상황에서만 사용한다.
- 트러블슈팅은 `19-Blue-Green-트러블슈팅.md`에 누적하고, 최종 요약은 본 문서를 기준으로 업데이트한다.

---

## 7. 관련 문서
- 런북: [20-Blue-Green-운영-적용-런북.md](20-Blue-Green-운영-적용-런북.md)
- 트러블슈팅: [19-Blue-Green-트러블슈팅.md](19-Blue-Green-트러블슈팅.md)
- 초기 설계/구성: [18-Blue-Green-런북.md](18-Blue-Green-런북.md)

---

## 8. 결론
- Blue/Green 운영 적용은 실제 전환(blue, green)까지 완료했고 현재 기준 색상은 `green(8082)`다.
- 전환 절차는 readiness 확인과 공개 도메인 검증을 모두 포함하는 형태로 정리됐다.
- 운영 자동화도 Blue/Green 전용 스크립트 기준으로 재구성돼, 다음 교대 전환을 같은 방식으로 재현할 수 있다.
