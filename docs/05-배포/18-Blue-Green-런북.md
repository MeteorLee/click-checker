# 18. Blue/Green 런북

## 문서 목적

단일 EC2 환경에서 `app-blue`와 `app-green` 두 애플리케이션 컨테이너를 번갈아 활성화하는 절차를 정리한다.  
이 문서는 운영 적용 전 기준 런북이며, 현재까지는 로컬 검증이 완료된 상태를 기준으로 작성한다.

## 현재 전제

- 운영 진입점은 `https://clickchecker.dev`
- 앞단은 `nginx`
- 애플리케이션 컨테이너는 `app-blue`, `app-green`
- 내부 포트는 다음과 같이 고정
  - blue: `8081`
  - green: `8082`
- readiness 확인 경로
  - `/actuator/health/readiness`

## 목표

- 새 색상 컨테이너를 먼저 기동한다.
- readiness가 `UP`인지 확인한다.
- `nginx` upstream을 새 색상으로 전환한다.
- 이전 색상 컨테이너를 종료한다.

## 현재 확인된 범위

- `app-blue`, `app-green` 동시 기동 가능
- 두 컨테이너 모두 readiness `UP`
- 루트 응답으로 색상 구분 가능
  - blue: `color=blue`
  - green: `color=green`
- 임시 `nginx` 컨테이너 기준 upstream 전환 검증 완료
  - blue 응답 확인
  - upstream 변경 후 green 응답 확인

## 전환 절차

### 1. 현재 활성 색상 확인

- 현재 `nginx` upstream이 어느 포트를 보고 있는지 확인한다.
- 기준 파일: `nginx/blue-green-click-checker.conf`
- 확인 포인트:
  - `server app-blue:8081;`
  - 또는 `server app-green:8082;`

### 2. 비활성 색상 컨테이너 기동

예시:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build app-green
```

### 3. readiness 확인

예시:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T app-green wget -qO- http://localhost:8082/actuator/health/readiness
```

기대 결과:

```json
{"status":"UP"}
```

### 4. 전환 대상 응답 확인

필요 시 루트 응답에서 색상을 확인한다.

예시:

```bash
curl -s http://127.0.0.1:8082/
```

기대 결과:

```json
{"service":"click-checker","status":"ok","health":"/actuator/health","color":"green"}
```

### 5. nginx upstream 전환

`nginx/blue-green-click-checker.conf`의 active upstream 한 줄을 새 색상으로 변경한다.

예시:

```nginx
upstream click_checker_app {
    server app-green:8082;
}
```

### 6. nginx 설정 반영

```bash
sudo cp ~/click-checker/nginx/blue-green-click-checker.conf /etc/nginx/sites-available/default
sudo nginx -t
sudo systemctl reload nginx
```

검증용 임시 nginx 컨테이너를 사용할 때는 `reload` 대신 재생성 기준으로 반영한다.

예시:

```bash
docker rm -f click-checker-nginx-bg-test
docker run --rm -d \
  --name click-checker-nginx-bg-test \
  --network click-checker_default \
  -p 18080:80 \
  -v "$PWD/nginx/blue-green-click-checker.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.25-alpine
```

### 7. 외부 응답 확인

```bash
curl -s https://clickchecker.dev
```

기대 결과:

- 응답 JSON의 `color`가 새 색상으로 변경됨

### 8. 이전 색상 종료

예시:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop app-blue
```

## 전환 스크립트

현재 검증용 전환 스크립트:

```bash
./scripts/blue-green-switch.sh
```

특징:

- 현재 활성 색상을 `nginx/blue-green-click-checker.conf`에서 읽는다.
- 반대 색상 컨테이너를 기동한다.
- readiness가 `UP`이 될 때까지 기다린다.
- upstream을 새 색상으로 변경한다.
- 검증용 임시 nginx 컨테이너를 **재생성**한다.
- 이전 색상 컨테이너를 종료한다.

색상을 명시해서 실행할 수도 있다.

```bash
./scripts/blue-green-switch.sh blue
./scripts/blue-green-switch.sh green
```

주의:

- 이 스크립트는 현재 **검증용 구조**를 기준으로 작성됐다.
- `nginx/blue-green-click-checker.conf`
- `click-checker-nginx-bg-test`
- 로컬 `.env`
를 전제로 한다.
- 실제 운영 nginx와 운영 배포 경로를 직접 전환하는 스크립트는 아직 아니다.

## 롤백 절차

- 새 색상이 readiness 실패 또는 응답 이상이면 `nginx` upstream을 이전 색상으로 되돌린다.
- `nginx -t` 후 `reload`한다.
- 필요 시 새 색상 컨테이너를 중지한다.

## 주의사항

- 현재 운영용 `nginx/click-checker.conf`와 Blue/Green 검증용 `nginx/blue-green-click-checker.conf`는 역할이 다르다.
- 운영 적용 전에는 반드시 두 파일의 역할을 구분해서 관리한다.
- 실제 운영 반영 전에는 `grafana.clickchecker.dev` HTTPS/Basic Auth 설정이 덮어써지지 않도록 주의한다.
- 검증용 임시 nginx에서 bind-mounted 단일 설정 파일을 바꿨을 때는 `reload`보다 재생성이 더 확실하다.

## 다음 작업

- 운영 nginx와 Blue/Green 전환용 nginx를 어떻게 합칠지 결정
- 실제 EC2 운영 경로에서 전환 실험 수행
- Blue/Green 시행착오를 별도 트러블슈팅 문서로 기록
