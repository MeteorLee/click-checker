# 23. CodeDeploy / S3 트러블슈팅

## 문서 목적

CodeDeploy / S3 / ECR 기반 배포로 전환하는 과정에서 실제로 발생한 문제와 확인 흐름을 기록한다.  
이번 문서는 최종 정답만 적는 문서가 아니라, 어디서 막혔고 무엇을 기준으로 수정했는지 남기는 데 목적이 있다.

## 1. 리전이 맞지 않아 CodeDeploy 애플리케이션을 찾지 못한 문제

### 상황

- GitHub Actions에서 `create-deployment` 단계가 실패했다.
- 로그에는 `ApplicationDoesNotExistException`이 출력됐다.

### 원인

- S3 버킷과 CodeDeploy 애플리케이션을 처음에 버지니아 리전에 만들었다.
- 반면 운영 EC2, RDS, GitHub Secrets는 서울 리전(`ap-northeast-2`) 기준으로 잡혀 있었다.

### 대응

- S3 버킷과 CodeDeploy 애플리케이션, 배포 그룹을 서울 리전 기준으로 다시 만들었다.
- 이후 GitHub Secrets도 서울 리전 기준 값으로 다시 맞췄다.

### 정리

- CodeDeploy와 운영 EC2는 같은 리전 기준으로 맞추는 것이 기본이다.
- 이번 단계에서는 리전 불일치가 가장 먼저 큰 장애로 드러났다.

## 2. GitHub Actions OIDC 역할은 만들었지만 초기 권한이 부족했던 문제

### 상황

- GitHub Actions에서 `AssumeRoleWithWebIdentity` 또는 CodeDeploy 조회 단계가 실패했다.
- 이후 `GetApplication`, `GetDeploymentGroup`, `GetApplicationRevision` 권한 부족이 순서대로 드러났다.

### 원인

- GitHub Actions 역할의 신뢰 정책과 인라인 정책이 처음에는 최소 기준보다 부족했다.
- 특히 CodeDeploy 배포 전 검증 로그를 추가하면서 조회 권한 부족이 더 분명하게 보였다.

### 대응

- 신뢰 정책은 `MeteorLee/click-checker` 저장소의 `prod` 브랜치만 허용하도록 정리했다.
- 인라인 정책은 다음 범위로 확장했다.
  - S3 업로드 / 조회
  - CodeDeploy 배포 생성 / 조회
  - ECR 이미지 push

### 정리

- 첫 연결 단계에서는 OIDC 신뢰 정책과 인라인 정책을 따로 봐야 한다.
- 이번 작업에서는 GitHub Actions 역할이 실제로 어떤 AWS 호출을 하는지 로그를 보강하면서 권한 범위를 정리했다.

## 3. EC2의 CodeDeploy Agent가 자격 증명을 읽지 못한 문제

### 상황

- CodeDeploy 배포는 시작됐지만 인스턴스 단계에서 lifecycle event 자체를 받지 못했다.
- 로그에는 `Missing credentials - please check if this instance was started with an IAM instance profile`가 남았다.

### 원인

- EC2에 IAM Role을 새로 연결한 뒤, CodeDeploy Agent가 아직 해당 자격 증명을 반영하지 못하고 있었다.

### 대응

- EC2에 역할이 실제로 붙어 있는지 다시 확인했다.
- CodeDeploy Agent를 재시작했다.
- 이후 agent 로그에서 서울 리전 endpoint를 다시 잡는지 확인했다.

### 정리

- EC2 IAM Role을 바꾼 뒤에는 CodeDeploy Agent 재시작이 필요할 수 있다.
- 이번 단계에서는 agent 로그 확인이 원인 파악에 결정적이었다.

## 4. CodeDeploy는 연결됐지만 EC2에서 Docker build까지 하면서 ApplicationStart가 시간 초과된 문제

### 상황

- `ApplicationStart` 훅이 300초, 이후 900초 기준에서도 오래 걸렸다.
- 로그를 보면 `starting app-blue with build` 이후 이미지 빌드 단계에서 오래 머물렀다.
- 배포 중 EC2 전체 반응도 함께 느려졌다.

### 원인

- 기존 SSH 방식에서 쓰던 구조를 그대로 가져오면서, 운영 서버가 여전히 Docker build를 수행하고 있었다.
- 이 구조는 SSH 배포에서는 버텼지만, CodeDeploy의 lifecycle event 안에서는 병목이 더 분명하게 드러났다.

### 대응

- 처음에는 timeout을 늘려 원인을 더 명확히 확인했다.
- 이후 방향을 바꿔 운영 서버 빌드를 제거했다.
  - GitHub Actions에서 `bootJar`
  - GitHub Actions에서 Docker image build
  - ECR push
  - EC2는 ECR pull 후 실행

### 정리

- 이번 단계에서 가장 중요한 구조 변경 포인트였다.
- 배포 서버는 실행과 전환을 맡고, 빌드는 CI가 담당하는 구조로 바뀌었다.

## 5. EC2에서 AWS CLI가 없어 ECR 로그인이 실패한 문제

### 상황

- `application-start.sh`가 `required command not found: aws`로 실패했다.

### 원인

- EC2에서 `aws ecr get-login-password`를 호출하도록 바뀌었지만, AWS CLI가 설치돼 있지 않았다.
- Ubuntu 24.04 환경에서는 `apt install awscli`가 바로 되지 않았다.

### 대응

- Ubuntu 24.04 기준으로 `snap install aws-cli --classic`을 사용해 AWS CLI를 설치했다.

### 정리

- ECR pull 구조로 바꾸면 EC2 실행 환경에도 AWS CLI가 필요하다.
- 이 단계는 코드 문제가 아니라 운영 환경 준비 문제였다.

## 6. 수동 docker compose 확인 시 APP_IMAGE가 없어 경고가 뜬 문제

### 상황

- 배포는 성공했지만, 운영자가 수동으로 `docker compose ... ps`를 실행하면 `APP_IMAGE`가 없어 compose가 깨졌다.

### 원인

- 자동 배포 중에는 `application-start.sh`가 `.env.codedeploy`를 읽어 `APP_IMAGE`를 export한다.
- 하지만 수동 쉘 세션에는 그 값이 자동으로 들어오지 않는다.

### 대응

- 수동 확인 시에는 `--env-file .env.codedeploy`를 사용해 현재 배포 이미지 정보를 함께 읽도록 했다.

### 정리

- 자동 배포와 수동 운영 명령은 환경변수 기준이 다를 수 있다.
- 이후 운영 확인용 명령은 문서에 같이 남겨두는 편이 좋다.

## 7. EC2 작업 디렉터리를 git 저장소처럼 보면 `git pull`이 막히는 문제

### 상황

- EC2에서 `git status`를 보면 `Dockerfile`, `docker-compose.prod.yml`, 배포 스크립트가 `modified`로 보였다.
- `.env.codedeploy`, `deployment/`, `appspec.yml`, `scripts/codedeploy/*` 같은 파일은 `untracked`로 쌓였다.
- 이 상태에서 `git pull`을 실행하면 로컬 수정 파일과 untracked 파일이 원격 파일을 덮어쓸 수 없다고 나오며 pull이 중단됐다.

### 원인

- 예전에는 EC2의 `~/click-checker`가 `git clone -> git pull -> docker build` 기준의 작업 디렉터리였다.
- CodeDeploy 전환 후에는 같은 경로를 배포 대상 디렉터리로도 사용하게 됐다.
- 즉 하나의 폴더가 동시에
  - git 작업 트리
  - CodeDeploy가 파일을 푸는 배포 디렉터리
  - `.env.codedeploy`, `deployment/`, backup dump 같은 운영 산출물 저장 위치
  역할을 같이 맡으면서 충돌이 생겼다.

### 대응

- EC2의 `~/click-checker`는 더 이상 `git pull` 기준 작업 공간으로 보지 않고, CodeDeploy가 덮어쓰는 배포 디렉터리로 해석한다.
- 운영 확인은 `git status`, `git pull` 대신 다음 기준으로 수행한다.
  - `docker ps`, `docker compose ps`
  - `health`, `readiness`
  - 현재 활성 색상(blue/green)
  - CodeDeploy 최근 배포 상태
- 코드 비교나 문서 확인이 필요하면 로컬 저장소 또는 별도 clone에서 수행한다.

### 정리

- 이 문제는 git 자체 오류가 아니라, 배포 방식이 `git pull`에서 `CodeDeploy`로 바뀐 뒤에도 같은 경로를 git 작업 트리처럼 본 데서 생긴 혼선이다.
- 현재 운영 기준에서 EC2는 소스 동기화 주체가 아니라 배포 실행 서버다.
- 따라서 `~/click-checker`는 운영 배포 디렉터리로 두고, 서버에서 직접 `git pull`하는 흐름은 운영 기준에서 제외하는 편이 맞다.

## 이번 단계에서 정리된 기준

- CodeDeploy / S3 / ECR 조합은 실제 배포 성공까지 확인했다.
- 운영 서버에서 직접 Docker build를 하지 않는 구조로 정리했다.
- GitHub Actions 역할과 EC2 역할은 분리해서 봐야 한다.
- EC2 작업 디렉터리는 git 작업 공간보다 배포 디렉터리로 보는 것이 현재 구조와 맞다.
- 리전, IAM, agent, 실행 환경 준비가 모두 맞아야 배포가 끝까지 성공한다.
