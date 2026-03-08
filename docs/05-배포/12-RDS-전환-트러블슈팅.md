# 12. RDS 전환 트러블슈팅

## 0. 목적
- 4단계 RDS 전환 과정에서 실제로 발생한 문제와 해결 과정을 기록한다.
- 다음 전환 작업에서 같은 시행착오를 줄인다.

---

## 1. Flyway 조회 시 DB 계정명 오류

### 증상
```bash
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL:  role "clickchecker" does not exist
```

### 원인
- 실제 `.env` 기준 계정명은 `clickchecker`가 아니라 `app`이었다.

### 해결
- 조회 명령을 아래처럼 수정했다.

```bash
docker compose exec -T postgres psql -U app -d click_checker -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

### 메모
- RDS 전환 전 기본 접속 정보는 반드시 `.env`와 compose 설정을 먼저 확인하고 사용한다.

---

## 2. RDS 보안 그룹에 개인 IP를 넣은 문제

### 증상
- RDS 접속을 위해 보안 그룹을 열려 했지만, 개인 IP 기준으로 규칙이 잡혀 있었다.

### 원인
- 이번 전환에서 실제로 붙어야 하는 대상은 개인 PC가 아니라 EC2 서버였다.

### 해결
- RDS 보안 그룹 인바운드 `5432` source를 EC2 기준으로 다시 설정했다.
- 최종적으로 EC2에서 `select 1` 접속 확인까지 완료했다.

### 메모
- RDS 보안 그룹은 개인 IP보다 EC2 보안 그룹 또는 EC2 private 네트워크 기준으로 여는 것이 맞다.

---

## 3. EC2 호스트에 `psql` 미설치

### 증상
```bash
Command 'psql' not found
```

### 원인
- EC2 호스트에는 PostgreSQL client가 설치되어 있지 않았다.

### 해결
- 호스트에 패키지를 추가 설치하지 않고, 기존 `postgres` 컨테이너 안의 `psql`을 재사용했다.

```bash
docker compose exec -T postgres env PGPASSWORD='<RDS_PASSWORD>' \
psql "host=<RDS_ENDPOINT> port=5432 dbname=click_checker user=app sslmode=require" \
-c "select 1;"
```

### 메모
- 이미 있는 컨테이너 도구를 재사용하는 쪽이 더 단순했다.

---

## 4. 대화형 `psql` 접속이 멈춘 것처럼 보인 문제

### 증상
- `psql -h ... -U app -d click_checker` 실행 후 비밀번호 입력 뒤 멈춘 것처럼 보였다.

### 원인
- 대화형 접속 모드라서 프롬프트가 바로 눈에 띄지 않았고, 확인용 명령으로는 적합하지 않았다.

### 해결
- 비대화형 방식으로 바꿔 즉시 성공/실패를 확인했다.

```bash
docker compose exec -T postgres env PGPASSWORD='<RDS_PASSWORD>' PGCONNECT_TIMEOUT=5 \
psql "host=<RDS_ENDPOINT> port=5432 dbname=click_checker user=app sslmode=require" \
-c "select 1;"
```

### 메모
- 접속 확인은 `-c "select 1;"` 방식이 더 명확하다.

---

## 5. `docker cp` 대상 컨테이너 이름 오인

### 증상
```bash
Error response from daemon: No such container: click-checker-postgres-1
```

### 원인
- 실제 컨테이너 이름은 `click-checker-postgres`였는데, 일반적인 compose suffix가 붙는다고 잘못 가정했다.

### 해결
- `docker compose ps`로 실제 이름을 먼저 확인한 뒤 복사했다.

```bash
docker cp backup-20260307-171505.dump click-checker-postgres:/tmp/backup.dump
```

### 메모
- 컨테이너 이름은 추정하지 말고 `docker compose ps`로 먼저 확인하는 게 안전하다.

---

## 6. prod compose가 여전히 로컬 postgres를 바라보던 문제

### 증상
- 앱은 RDS로 전환했는데, prod 설정과 배포 스크립트 일부는 여전히 로컬 `postgres`를 전제로 하고 있었다.

### 원인
- `docker-compose.prod.yml`의 `DB_URL`이 `postgres:5432` 고정값이었고,
- 배포 workflow도 `postgres` 컨테이너를 계속 같이 띄우고 있었다.

### 해결
- `docker-compose.prod.yml`이 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 `.env`에서 직접 읽도록 수정했다.
- `deploy-prod.yml`도 `DB_*` 필수값을 검사하도록 수정했다.
- 최종적으로 prod 배포 시 `postgres` 없이도 `app`이 RDS만 보고 뜨도록 정리했다.

### 메모
- RDS 전환은 앱 연결만 바꾸는 작업이 아니라, 배포 자동화와 compose 책임 분리까지 함께 맞춰야 완결된다.

---

## 7. 최종 정리
- row count, Flyway, path 집계, time-bucket 집계가 모두 전환 전 기준과 일치했다.
- prod는 최종적으로 로컬 `postgres` 없이 RDS만 보고 정상 동작한다.
