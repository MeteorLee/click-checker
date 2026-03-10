# 20. Blue/Green 운영 적용 런북

## 문서 목적

로컬에서 검증한 Blue/Green 구조를 실제 운영 환경에 처음 올리고, 이후 첫 교대 전환까지 수행하는 절차를 정리한다.  
처음 적용은 `8080 -> 8081(app-blue)`로 시작했고, 현재는 `8081(app-blue) -> 8082(app-green)` 교대 전환까지 완료된 상태를 기준으로 작성한다.

## 현재 전제

- 운영 도메인: `https://clickchecker.dev`
- Grafana 관리자 경로: `https://grafana.clickchecker.dev`
- 운영 nginx 메인 앱 경로는 현재 `127.0.0.1:8082(app-green)`을 보고 있다.
- 운영 Grafana 경로는 현재 설정 그대로 유지해야 한다.
- `app-blue`는 `8081`, `app-green`는 `8082`를 사용한다.
- 현재 운영 기본 색상은 `green(8082)`다.

## 이번 런북의 범위

- `8080` legacy 앱에서 `8081(app-blue)`로 첫 전환
- `8081(app-blue)`에서 `8082(app-green)`로 첫 교대 전환
- readiness 기반 확인
- 메인 앱 경로 전환
- 롤백 기준 정의

## 목표

- 새 색상 컨테이너를 운영 서버에서 안정적으로 기동한다.
- readiness `UP`를 확인한 뒤 운영 nginx의 메인 앱 경로를 새 색상으로 전환한다.
- 전환 후 `clickchecker.dev` 응답이 정상이고 `color`가 기대한 색상으로 바뀌는지 확인한다.
- 문제가 있으면 즉시 이전 색상으로 복구한다.

## 현재 결과

- 첫 운영 적용(`8080 -> 8081`)은 완료됐다.
- 첫 교대 전환(`8081 -> 8082`)도 완료됐다.
- `clickchecker.dev` 메인 앱은 현재 `app-green(8082)`를 보고 있다.
- 루트 응답에서 `color=green`이 확인됐다.
- `app(8080)` legacy 컨테이너와 `app-blue(8081)`는 중지했다.
- `grafana.clickchecker.dev`는 기존 설정 그대로 정상 동작했다.

## 사전 체크리스트

- 현재 운영 nginx 백업 완료
- 현재 운영 app(`8080`) 정상
- `clickchecker.dev`, `/actuator/health` 정상
- `grafana.clickchecker.dev` 정상
- EC2 자원 상태 확인
- `app-blue` 이미지 빌드 가능
- `app-blue` readiness 경로 확인 가능

## 적용 순서

### 1. 운영 nginx 백업

```bash
sudo cp /etc/nginx/sites-available/default /etc/nginx/sites-available/default.before-blue
```

### 2. `app-blue` 기동

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build app-blue
```

### 3. `app-blue` readiness 확인

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T app-blue \
  wget -qO- http://localhost:8081/actuator/health/readiness
```

기대 결과:

```json
{"status":"UP"}
```

### 4. `app-blue` 루트 응답 확인

```bash
curl -s http://127.0.0.1:8081/
```

기대 결과:

- `status=ok`
- `color=blue`

### 5. 운영 nginx upstream 전환

운영 nginx 설정 파일의 메인 앱 upstream을 `127.0.0.1:8081`로 변경한다.

기준 예시:

```nginx
upstream click_checker_app {
    server 127.0.0.1:8081;
}
```

주의:

- Grafana 블록은 절대 수정하지 않는다.
- `clickchecker.dev` 메인 앱 upstream만 바꾼다.

### 6. nginx 검증 및 적용

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 7. 운영 도메인 검증

```bash
curl -s https://clickchecker.dev
curl -s https://clickchecker.dev/actuator/health
```

확인 포인트:

- 메인 루트 응답 정상
- `color=blue`
- `/actuator/health`는 200
- `grafana.clickchecker.dev`도 기존처럼 정상 접근

### 8. 이전 색상 정리 여부 판단

전환 직후에는 이전 색상을 바로 내리지 않고 잠시 관찰할 수 있다.  
문제가 없다고 판단되면 이전 색상을 종료한다.

실제 적용 결과:

- 첫 적용 후 `app(8080)` legacy 컨테이너를 중지했다.
- 교대 전환 후 `app-blue(8081)` 컨테이너도 중지했다.
- 운영 메인 경로는 현재 `app-green(8082)`만 보도록 정리했다.

## 롤백 절차

다음 상황이면 즉시 롤백한다.

- 새 색상 readiness 실패
- 전환 직후 `clickchecker.dev` 응답 실패
- 메인 루트 응답의 `color`가 예상과 다름
- `grafana.clickchecker.dev` 이상
- 5xx 응답 증가

롤백 순서:

1. 운영 nginx 메인 앱 경로를 이전 색상 포트로 되돌린다.
2. `sudo nginx -t`
3. `sudo systemctl reload nginx`
4. 필요 시 새 색상 컨테이너를 종료한다.

## 전환 후 확인 항목

- `clickchecker.dev` 응답 정상
- 루트 응답 `color=green`
- `/actuator/health` 200
- `app-green` healthy
- `grafana.clickchecker.dev` 정상

## 다음 단계

- 현재 운영 기본 색상은 `green(8082)`다.
- 다음 교대 전환은 `8082(green) -> 8081(blue)` 순서로 수행한다.
- 운영 nginx 메인 앱 경로는 현재 직접 포트 참조 방식이므로, 후속으로 `upstream click_checker_app` 구조로 정리한다.
