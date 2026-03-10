# 20. Blue/Green 운영 적용 런북

## 문서 목적

로컬에서 검증한 Blue/Green 구조를 실제 운영 환경에 처음 올릴 때의 절차를 정리한다.  
현재 운영은 `127.0.0.1:8080`의 단일 앱을 기준으로 안정화돼 있으므로, 첫 운영 적용은 `8080 -> 8081(app-blue)` 전환으로 제한한다.

## 현재 전제

- 운영 도메인: `https://clickchecker.dev`
- Grafana 관리자 경로: `https://grafana.clickchecker.dev`
- 운영 nginx 메인 앱 upstream은 이제 `127.0.0.1:8081(app-blue)` 기준으로 전환 완료
- 운영 Grafana 경로는 현재 설정 그대로 유지해야 한다.
- `app-blue`는 `8081`, `app-green`는 `8082`를 사용한다.

## 이번 런북의 범위

- `8080` legacy 앱에서 `8081(app-blue)`로 첫 전환
- readiness 기반 확인
- 메인 앱 upstream 전환
- legacy 앱 롤백 기준 정의

이번 문서는 아직 `8081 <-> 8082` 반복 교대 전체를 다루지 않는다.

## 목표

- `app-blue`를 운영 서버에서 안정적으로 기동한다.
- readiness `UP`를 확인한 뒤 운영 nginx의 메인 앱 upstream을 `8081`로 전환한다.
- 전환 후 `clickchecker.dev` 응답이 정상이고 `color=blue`를 반환하는지 확인한다.
- 문제가 있으면 즉시 `8080` legacy 앱으로 복구한다.

## 현재 결과

- 첫 운영 적용은 완료됐다.
- `clickchecker.dev` 메인 앱은 현재 `app-blue(8081)`를 보고 있다.
- 루트 응답에서 `color=blue`가 확인됐다.
- `app(8080)` legacy 컨테이너는 중지했다.
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

### 8. legacy `8080` 앱 정리 여부 판단

첫 운영 적용에서는 전환 직후 바로 `8080` 앱을 내리지 않는다.  
일정 시간 관찰 후 문제가 없다고 판단되면 종료 시점을 별도로 결정한다.

실제 적용 결과:

- 초기 관찰 후 `app(8080)` legacy 컨테이너를 중지했다.
- 운영 메인 경로는 `app-blue(8081)`만 보도록 정리했다.

## 롤백 절차

다음 상황이면 즉시 롤백한다.

- `app-blue` readiness 실패
- 전환 직후 `clickchecker.dev` 응답 실패
- 메인 루트 응답의 `color`가 예상과 다름
- `grafana.clickchecker.dev` 이상
- 5xx 응답 증가

롤백 순서:

1. 운영 nginx upstream을 `127.0.0.1:8080`으로 되돌린다.
2. `sudo nginx -t`
3. `sudo systemctl reload nginx`
4. 필요 시 `app-blue`를 종료한다.

## 전환 후 확인 항목

- `clickchecker.dev` 응답 정상
- 루트 응답 `color=blue`
- `/actuator/health` 200
- `app-blue` healthy
- `grafana.clickchecker.dev` 정상

## 다음 단계

- 첫 운영 적용이 안정화되면 `8081(app-blue)`를 기준 운영 상태로 본다.
- 그 다음부터는 `8081(blue) <-> 8082(green)` 교대 구조로 확장한다.
