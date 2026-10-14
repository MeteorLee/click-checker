# EC2 · RDS 중지 후 재시작 체크리스트

기존 EC2와 RDS를 중지했다가 다시 시작하고, 서비스가 정상 동작하는지 확인하는 절차다.
기존 컨테이너가 정상적으로 올라오면 새 배포는 필요 없다.

## 1. RDS 시작

- AWS 콘솔에서 RDS 인스턴스를 시작한다.
- 상태가 `사용 가능(Available)`이 될 때까지 기다린다.

## 2. EC2 시작 및 IP 확인

- AWS 콘솔에서 EC2 인스턴스를 시작한다.
- 인스턴스 상세 화면에서 퍼블릭 IPv4 주소를 확인한다.
- 이번 재시작에서 확인한 새 IP는 `3.36.73.241`이다. 다음 재시작 때도 같은 값이라고 가정하지 않고 다시 확인한다.

## 3. DNS와 SSH 접속 주소 수정

퍼블릭 IP가 변경됐다면 도메인과 SSH 접속 주소를 새 IP로 맞춘다.
이번에는 기존 `3.38.151.180`에서 `3.36.73.241`로 변경됐다.

### Route 53의 A 레코드 변경

1. [AWS Route 53 콘솔](https://console.aws.amazon.com/route53/)에 접속한다.
2. 왼쪽 메뉴의 `호스팅 영역(Hosted zones)`을 선택한다.
3. `clickchecker.dev`의 퍼블릭 호스팅 영역을 연다.
4. 이름이 `clickchecker.dev`, 유형이 `A`인 레코드를 선택한다.
5. `레코드 편집(Edit record)`을 누른다.
6. `값(Value)`의 기존 IP를 새 EC2 IP로 변경하고 저장한다.
7. `grafana.clickchecker.dev`도 별도 A 레코드로 기존 IP를 사용한다면 동일하게 변경한다.

- Grafana가 `clickchecker.dev`를 가리키는 CNAME이라면 IP를 직접 넣지 않는다.
- `NS`, `SOA` 레코드와 다른 서비스의 레코드는 수정하지 않는다.
- DNS 캐시 때문에 기존 IP가 TTL 동안 보일 수 있다.

EC2에서 반영 여부를 확인한다.

```bash
getent ahostsv4 clickchecker.dev
getent ahostsv4 grafana.clickchecker.dev
```

각 도메인이 새 EC2 IP를 반환하는지 확인한다.
DNS 반영 전에도 새 서버의 HTTPS 응답은 다음 명령으로 확인할 수 있다.

```bash
curl -i --max-time 10 --resolve clickchecker.dev:443:3.36.73.241 https://clickchecker.dev/healthz
curl -i --max-time 10 --resolve clickchecker.dev:443:3.36.73.241 https://clickchecker.dev/actuator/health
```

다음 재시작에서는 위 명령의 IP도 당시 확인한 새 IP로 바꾼다.

### SSH 접속 주소 변경

직접 IP로 접속한다면 기존 명령의 IP만 새 값으로 바꾼다.
키 파일 경로는 실제 사용하는 경로로 지정한다.

```bash
ssh -i "<키 파일 경로>" ubuntu@3.36.73.241
```

SSH 별칭을 사용한다면 로컬 `~/.ssh/config`에서 해당 서버의 `HostName`을 변경한다.
Windows에서는 보통 `C:\Users\ghtmd\.ssh\config`를 사용하며, WSL에서 SSH를 실행하면 WSL의 `~/.ssh/config`를 확인한다.

```sshconfig
Host click-checker
    HostName 3.36.73.241
    User ubuntu
    IdentityFile <키 파일 경로>
```

기존 `Host` 별칭, 사용자와 키 파일 경로는 유지하고 `HostName`만 변경한다.
별칭을 사용하지 않는다면 SSH 설정 파일을 새로 만들 필요는 없다.

참고: [AWS Route 53 레코드 편집 안내](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resource-record-sets-editing.html)

## 4. 서버 상태 확인

EC2에 접속해 Docker, Nginx, 컨테이너 상태를 확인한다.

```bash
systemctl is-active docker nginx
sudo docker ps -a
```

Docker와 Nginx가 `active`인지, 운영 중인 백엔드와 프런트엔드가 `healthy`인지 확인한다.
비활성 색상이나 사용하지 않는 legacy 컨테이너까지 모두 실행할 필요는 없다.

## 5. 비정상 컨테이너 점검

비정상 컨테이너는 로그와 헬스체크 실패 내용을 확인한다.
프런트엔드 기준 예시는 다음과 같다.

```bash
sudo docker logs --tail 60 click-checker-frontend-1
sudo docker inspect --format '{{json .State.Health}}' click-checker-frontend-1
```

이번 프런트엔드는 헬스체크의 `localhost:3000`에서 연결이 거부됐지만, `127.0.0.1:3000`에서는 정상 응답했다.
헬스체크 주소를 `127.0.0.1`로 변경하고 컨테이너를 재생성한 뒤 `healthy`를 확인했다.
수정된 설정이 유지되면 반복할 작업은 아니다.

Compose로 컨테이너를 재생성할 때는 `APP_IMAGE`와 `FRONTEND_IMAGE` 값도 전달해야 한다.
기존 컨테이너가 남아 있다면 사용하는 이미지 주소를 조회해 전달할 수 있다.

```bash
APP_IMAGE=$(sudo docker inspect --format '{{.Config.Image}}' click-checker-app-green-1)
FRONTEND_IMAGE=$(sudo docker inspect --format '{{.Config.Image}}' click-checker-frontend-1)

sudo env APP_IMAGE="$APP_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  up -d --no-deps --force-recreate frontend
```

위 예시는 EC2의 `~/click-checker`에서 실행하며, 기존 `app-green` 컨테이너가 존재하는 경우를 기준으로 한다.
프런트엔드 재생성 중에는 화면 접속이 잠시 중단될 수 있다.

## 6. 외부 접속과 DB 동작 확인

DNS 반영 후 공개 도메인으로 응답을 확인한다.

```bash
curl -i --max-time 10 https://clickchecker.dev/healthz
curl -i --max-time 10 https://clickchecker.dev/actuator/health
```

- 프런트엔드: `200 OK`, `{"status":"ok"}` 확인
- 백엔드: `200 OK`, `status`가 `UP`인지 확인
- 브라우저에서 로그인과 기존 데이터 조회 확인
- `https://grafana.clickchecker.dev` 접속 확인

헬스체크 응답만으로 모든 DB 동작을 확인했다고 판단하지 않고, 실제 로그인과 데이터 조회까지 확인한다.
