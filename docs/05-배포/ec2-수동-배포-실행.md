# EC2 수동 배포 실행 가이드 (1회)

## 0. 목적
- EC2에서 수동 배포를 1회 완주한다.
- 실패 시 원인 확인/복구까지 최소 루프를 경험한다.

## 1. 사전 준비
### 1.1 EC2 기본
- OS: Ubuntu 22.04 이상 권장
- 접속: SSH 키 준비
- 보안그룹:
  - `22` (SSH)
  - `8080` (앱 직접 확인용, 필요 시)
  - 운영에서는 리버스 프록시(80/443) 사용 권장

### 1.2 필수 설치
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
```

#### Docker 설치
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
docker --version
docker compose version
```

### 1.3 애플리케이션 환경 변수
- 프로젝트 루트에 `.env` 파일 생성
```env
POSTGRES_DB=click_checker
POSTGRES_USER=app
POSTGRES_PASSWORD=apppw
SENTRY_DSN=YOUR_SENTRY_DSN
```

## 2. 배포 절차 (수동 1회)
### 2.1 코드 준비
```bash
# 최초
cd ~
git clone <YOUR_REPO_URL> click-checker
cd click-checker

# 재배포
cd ~/click-checker
git pull
```

### 2.2 Build
```bash
./gradlew clean bootJar --no-daemon
```
- 실패 시: 즉시 중단

### 2.3 Test
```bash
./gradlew test --no-daemon
```
- 실패 시: 즉시 중단

### 2.4 Deploy
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build app postgres prometheus grafana
```

## 3. 배포 검증
### 3.1 health
```bash
curl -f http://localhost:8080/actuator/health
```
- 성공 기준: HTTP 200 + status=UP

### 3.2 기능 스모크
1. Organization 생성
```bash
ORG_ID=$(curl -sS -X POST http://localhost:8080/api/organizations \
  -H 'Content-Type: application/json' \
  -d '{"name":"ec2-deploy-check-org"}' | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "ORG_ID=${ORG_ID}"
```

2. 이벤트 저장
```bash
curl -sS -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{
    "organizationId": '"${ORG_ID}"',
    "eventType": "click",
    "path": "/__test/ec2-deploy-check",
    "occurredAt": "2026-02-28T00:00:00"
  }'
```

3. 집계 조회
```bash
curl -sS "http://localhost:8080/api/events/aggregates/paths?organizationId=${ORG_ID}&from=2020-01-01T00:00:00&to=2030-01-01T00:00:00&top=5"
```

## 4. 실패 시 점검
### 4.1 컨테이너 상태
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

### 4.2 로그 확인
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=200 postgres
```

### 4.3 대표 장애 대응
- 앱 기동 실패: 환경변수/DB 연결 정보 점검
- health 실패: app 로그에서 예외 스택 확인 후 재배포
- DB 접속 실패: postgres 컨테이너 상태/포트/볼륨 점검

## 5. 최소 롤백
- 직전 정상 커밋으로 복귀 후 재배포
```bash
git log --oneline -n 5
git checkout <LAST_GOOD_COMMIT>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build app postgres prometheus grafana
```
- 롤백 후 health 재확인

## 5.1 Grafana 확인(운영 점검용)
- prod 설정에서 Grafana는 `127.0.0.1:3000`으로만 바인딩된다.
- EC2 외부에서 직접 접근하지 않고 SSH 터널로 접속한다.
```bash
ssh -i <KEY.pem> -L 3000:localhost:3000 ubuntu@<EC2_PUBLIC_IP>
```
- 로컬 브라우저에서 `http://localhost:3000` 접속

## 6. 완료 기준
- build/test/deploy 순서 완주
- health 통과
- 이벤트 저장 + 집계 조회 스모크 통과
- 실패 시 로그 기반 원인 파악/복구 경험 확보
