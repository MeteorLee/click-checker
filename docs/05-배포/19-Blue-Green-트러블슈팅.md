# 19. Blue/Green 트러블슈팅

## 목적

단일 EC2 Blue/Green 구조를 준비하고 로컬 검증하는 과정에서 실제로 맞닥뜨린 문제와 해결 방법을 기록한다.

## 1. `prometheus -> app` 의존 때문에 prod compose가 깨진 문제

### 증상

- prod compose에서 기존 `app`를 숨기고 `app-blue`, `app-green` 구조로 바꾸자 `prometheus` 의존성이 깨졌다.

### 원인

- base `docker-compose.yml`에 `prometheus -> app` 의존성이 있었다.
- prod에서는 `app`를 `profiles: ["legacy"]`로 숨겼기 때문에 base 의존성이 그대로 남아 충돌했다.

### 해결

- base에서 `prometheus -> app` 의존을 제거했다.
- local은 `docker-compose.local.yml`에서 `prometheus -> app`
- prod는 `docker-compose.prod.yml`에서 `prometheus -> app-blue`
로 역할을 분리했다.

## 2. healthcheck에서 `${SERVER_PORT}`가 그대로 풀리지 않은 문제

### 증상

- `app-blue`, `app-green` healthcheck가 기대대로 동작하지 않았다.

### 원인

- compose YAML의 `${...}` 치환과 서비스 `environment`는 동일한 평가 시점이 아니다.
- `environment`에 넣은 `SERVER_PORT` 값을 healthcheck에서 그대로 재사용할 수 없었다.

### 해결

- 공통 healthcheck를 포기하고,
- `app-blue`는 `8081`
- `app-green`는 `8082`
를 healthcheck에 직접 명시했다.

## 3. Blue/Green용 prod env에 `API_KEY_*`가 빠진 문제

### 증상

- `app-blue`, `app-green` 기동 시 `API_KEY_PEPPER must not be blank` 예외가 발생했다.

### 원인

- prod 공통 env 앵커를 만들면서 `DB_*`, `SENTRY_DSN`만 넣고
- `API_KEY_PEPPER`, `API_KEY_ENV`를 누락했다.

### 해결

- `docker-compose.prod.yml`의 prod 공통 env에
  - `API_KEY_PEPPER`
  - `API_KEY_ENV`
를 추가했다.

## 4. repo nginx와 실제 운영 nginx가 갈라진 문제

### 증상

- Blue/Green upstream 구조를 repo nginx에 반영하는 과정에서
- 실제 EC2의 nginx 설정과 git 기준 파일이 달라진 상태를 확인했다.

### 원인

- `certbot --nginx`가 운영 서버의 `/etc/nginx/sites-available/default`를 직접 수정했다.
- 그 결과 `clickchecker.dev` HTTPS와 `grafana.clickchecker.dev` HTTPS/Basic Auth 설정이 서버 기준으로만 반영돼 있었다.

### 해결

- 먼저 실제 EC2 nginx 설정을 기준으로 repo `nginx/click-checker.conf`를 다시 맞췄다.
- 그 다음 Blue/Green 검증은 운영 파일을 건드리지 않기 위해
  `nginx/blue-green-click-checker.conf`
  별도 파일로 분리했다.

## 5. 운영 nginx를 바로 바꾸면 Grafana HTTPS를 덮어쓸 위험이 있던 문제

### 증상

- 메인 앱 Blue/Green 전환만 검증하려고 했는데
- 운영용 nginx 파일을 그대로 수정하면 Grafana HTTPS 설정까지 같이 흔들릴 수 있었다.

### 원인

- 운영 nginx는
  - `clickchecker.dev`
  - `grafana.clickchecker.dev`
  - HTTPS
  - Basic Auth
를 모두 포함한 상태였다.
- Blue/Green 실험은 메인 앱 전환만 검증하면 됐는데 범위를 분리하지 않았다.

### 해결

- 운영용 nginx와 실험용 nginx를 분리했다.
- 검증은 임시 nginx 컨테이너와 별도 설정 파일로만 진행했다.

## 6. 운영 nginx 대신 임시 nginx 컨테이너가 필요했던 이유

### 배경

- 현재 운영 진입점은 이미 `clickchecker.dev + HTTPS`로 운영 중이다.
- 이 상태에서 host nginx를 직접 바꾸면 실제 서비스 경로를 흔들 수 있다.

### 해결

- `nginx:1.25-alpine` 임시 컨테이너를 띄우고,
- `blue-green-click-checker.conf`를 마운트해
- `18080` 포트에서만 Blue/Green 전환을 검증했다.

## 7. 최종적으로 확인한 것

- `app-blue`, `app-green` 동시 기동 가능
- 둘 다 readiness `UP`
- 루트 응답으로 색상 구분 가능
- 임시 nginx upstream을
  - `app-blue:8081`
  - `app-green:8082`
로 바꿨을 때
응답 `color`가 실제로 바뀌는 것까지 확인했다.

## 8. bind-mounted nginx 파일은 `reload`만으로 최신 설정이 반영되지 않은 문제

### 증상

- `blue-green-click-checker.conf`에서 upstream을 `green -> blue`로 다시 바꿨는데
- 임시 nginx 컨테이너는 계속 이전 upstream(`app-green:8082`)을 보고 있었다.
- 그 결과 `green` 컨테이너를 내린 뒤에도 `502 Bad Gateway`가 발생했다.

### 원인

- 임시 nginx 컨테이너는 단일 설정 파일을 bind mount 해서 사용했다.
- 파일을 `sed -i`로 바꾸는 과정에서 inode가 교체되었고,
- nginx `reload`만으로는 컨테이너 내부에서 기대한 방식으로 최신 파일을 다시 읽지 못했다.

### 해결

- 전환용 임시 nginx는 `reload` 대신 **컨테이너 재생성** 방식으로 바꿨다.
- 즉,
  - 기존 임시 nginx 제거
  - 같은 이름으로 다시 실행
  - 최신 `blue-green-click-checker.conf` 재마운트
순서로 처리한다.

### 정리

- 운영 nginx에는 그대로 `reload`를 사용할 수 있다.
- 다만 이번처럼 검증용 단일 파일 bind mount 구조에서는
  `reload`보다 **재생성**이 더 확실하다.

## 9. CI 배포가 `app`와 `app-blue`를 함께 건드린 문제

### 증상

- 운영 첫 적용 전에 GitHub Actions 배포를 돌리자
  - `click-checker-app`
  - `click-checker-app-blue-1`
  두 컨테이너가 같이 올라오려 했다.
- health 검증은 실패했고, 로그에는 `app` 컨테이너의 datasource 설정 실패가 남았다.

### 원인

- 배포 경로가 아직 기존 `app` 중심 compose 호출을 포함하고 있었다.
- `app` 서비스는 더 이상 현재 prod 기준 서비스가 아닌데도 호출 대상에 남아 있었다.
- 그 결과 `app`은 prod용 env 없이 default profile로 올라오며 실패했다.

### 해결

- 운영 첫 적용은 CI 자동 배포 흐름이 아니라 수동 전환 절차로 진행했다.
- 실제 운영 서버에서는
  - `app-blue`를 별도로 기동
  - readiness 확인
  - 운영 nginx 메인 앱 upstream만 `8080 -> 8081`
  순서로 적용했다.
- 이후 CI 배포 경로를 `app` 중심 호출에서 분리하고, Blue/Green 전용 배포 스크립트(`scripts/deploy-prod-orchestrator.sh`)를 호출하는 구조로 재구성했다.

### 정리

- Blue/Green을 도입한 뒤에는 기존 `app` 중심 배포 경로를 그대로 두면 안 된다.
- 운영 자동화도 `app-blue`, `app-green` 기준 호출로 맞춰야 한다.

## 10. `appleboy/ssh-action` inline 스크립트 파싱 오류

### 증상

- GitHub Actions에서 배포 시작 직후 다음 오류가 반복됐다.
  - `bash: -c: line 126: syntax error near unexpected token ';'`
- 원격 EC2 로그 이전 단계에서 실패해, 서버 로직보다 workflow script 파싱 문제로 판단됐다.

### 원인

- `deploy-prod.yml`의 SSH 액션 `script:` 블록에 긴 bash 로직이 그대로 들어가 있었다.
- 로컬 shell 문법 검사(`bash -n`)는 통과해도, action 래퍼/원격 실행 경로에서 파싱 실패가 발생했다.

### 해결

- 배포 핵심 로직을 `scripts/deploy-prod-orchestrator.sh`로 분리했다.
- workflow는 다음 최소 단계만 수행하도록 단순화했다.
  - `git fetch/checkout/pull`
  - 실행 권한 부여
  - 배포 스크립트 실행

### 정리

- SSH 액션 inline 스크립트가 길어질수록 디버깅과 파싱 안정성이 급격히 나빠진다.
- 운영 배포 로직은 repo 스크립트로 분리하는 것이 더 안전하다.

## 11. 실제 운영 `blue -> green` 전환 이후 운영 nginx upstream 구조 반영

### 증상

- 실제 운영 첫 교대 전환은 `proxy_pass` 직접 포트 변경으로 수행됐다.

### 원인

- 운영 안정성을 우선해 최소 변경으로 교대 전환을 먼저 완료했다.
- 그 시점에는 운영 nginx를 `upstream click_checker_app` 구조로 재정리하지 않은 상태였다.

### 해결

- `blue -> green` 전환 성공 확인 후 운영 nginx를 upstream 구조로 반영했다.
- 현재는 `upstream click_checker_app` 타겟만 변경하면 색상 전환이 가능하다.

### 정리

- 운영 교대 전환은 성공했고, 이후 운영 nginx까지 upstream 구조로 정리해 하드코딩 의존을 줄였다.

## 정리

이번 단계에서 가장 중요했던 판단은 다음 여섯 가지였다.

1. 공통 compose와 환경별 compose의 역할을 다시 분리한 것
2. 운영 nginx와 Blue/Green 검증용 nginx를 분리한 것
3. 검증용 nginx는 `reload`보다 재생성 전략이 더 안전하다는 점을 확인한 것
4. 운영 첫 적용은 기존 CI 배포 흐름을 그대로 재사용하지 않고 수동 절차로 분리한 것
5. SSH 액션 inline 스크립트를 배포 전용 파일로 분리해 파싱 오류를 제거한 것
6. 수동 교대 검증 후 운영 nginx도 upstream 구조로 정리해 다음 전환 난이도를 낮춘 것

이 판단들을 먼저 바로잡은 뒤에야 Blue/Green 전환 자체를 안전하게 검증할 수 있었다.
