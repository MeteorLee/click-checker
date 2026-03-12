# 24. CodeDeploy / S3 종합

## 문서 목적

CodeDeploy / S3 / ECR 기반 배포 구조로 전환한 결과를 정리한다.  
이번 문서는 준비 문서나 트러블슈팅 문서와 다르게, 최종적으로 어떤 구조가 운영 기준이 됐는지 요약하는 데 목적이 있다.

## 1. 변경 배경

기존 운영 배포는 `GitHub Actions -> SSH -> EC2` 구조였다.  
이 흐름은 단순했지만, 운영 서버에서 직접 Docker build를 수행하는 구조를 그대로 포함하고 있었다.

CodeDeploy 전환 초반에는 이 구조를 유지한 채 전달 방식만 바꾸려 했다.  
하지만 실제 적용 과정에서 `ApplicationStart` 훅 안에 Docker build가 들어가면서 배포 시간이 길어지고, EC2 전체 반응도 느려지는 문제가 분명하게 드러났다.

결국 이번 단계에서는 단순히 전달 방식을 바꾸는 데서 끝나지 않고, 운영 서버 빌드를 제거하는 방향까지 함께 정리했다.

## 2. 최종 배포 흐름

최종 흐름은 다음과 같다.

```text
GitHub Actions
-> bootJar
-> Docker image build
-> ECR push
-> 배포 번들 zip 생성
-> S3 업로드
-> CodeDeploy deployment 생성
-> EC2에서 hook 실행
-> ECR image pull
-> Blue/Green 전환
-> health / smoke 확인
```

즉 역할은 다음처럼 나뉜다.

- GitHub Actions
  - 테스트 및 산출물 준비
  - Docker image build
  - ECR push
  - S3 / CodeDeploy 호출
- EC2
  - ECR 이미지 pull
  - 기존 Blue/Green 전환 스크립트 실행
  - 운영 검증 수행

## 3. 핵심 변경 사항

### 3.1 GitHub Actions

- `deploy-prod.yml`이 더 이상 SSH로 EC2에 직접 접속하지 않는다.
- 대신 AWS OIDC 역할을 사용해 S3, CodeDeploy, ECR을 호출한다.
- `bootJar` 생성 후 Docker 이미지를 빌드하고 ECR에 push한다.
- 배포 번들 안에는 이번 배포 대상 이미지 정보를 함께 포함한다.

### 3.2 CodeDeploy hook

- `AfterInstall`
  - 필수 파일 확인
  - 실행 권한 정리
  - 배포 대상 이미지 정보 정리
- `ApplicationStart`
  - ECR 로그인
  - 이미지 pull
  - 기존 `deploy-prod-orchestrator.sh` 실행
- `ValidateService`
  - 공개 health 재확인

### 3.3 Docker / Compose

- 운영 서버는 더 이상 Docker build를 하지 않는다.
- `docker-compose.prod.yml`은 고정 이미지 이름 대신 `APP_IMAGE`를 사용한다.
- Blue/Green 전환 스크립트는 `--build` 없이 이미 준비된 이미지를 실행한다.

## 4. 운영 기준

이번 전환 이후 운영 기준은 다음과 같이 정리된다.

- 운영 서버는 빌드 서버가 아니다.
- 빌드와 이미지 생성은 GitHub Actions가 맡는다.
- EC2는 실행, 전환, 검증을 담당한다.
- EC2의 배포 경로는 `git pull` 기준 작업 공간보다 CodeDeploy가 덮어쓰는 배포 디렉터리로 보는 편이 맞다.
- 실제 운영 정리 후 EC2의 `/home/ubuntu/click-checker`에는 `.git`을 두지 않고, 서버 전용 `.env`만 유지한 채 CodeDeploy가 나머지 파일을 다시 채우는 구조로 사용한다.
- 배포 실패 시에는 기존 활성 색상 유지 또는 복구가 우선이다.

즉 이번 단계에서 가장 크게 바뀐 것은 전달 도구만이 아니라, 배포 책임 분리 기준이다.

## 5. 확인된 결과

- CodeDeploy / S3 / ECR 기반 배포 성공
- EC2가 ECR SHA 태그 이미지를 pull해 `app-blue/app-green` 중 하나를 실행하는 것 확인
- Blue/Green 전환과 health / smoke 검증이 기존 구조와 충돌 없이 동작하는 것 확인
- 운영 서버에서 직접 Docker build를 제거한 뒤 `ApplicationStart` 병목 문제를 해소

## 6. 이번 단계에서 남긴 기준

- 배포 구조를 바꿀 때는 전달 방식만이 아니라 운영 서버 책임도 같이 봐야 한다.
- CodeDeploy 전환에서는 리전, IAM, agent, 실행 환경 준비가 모두 중요했다.
- 운영 서버에서 빌드를 제거한 뒤에야 CodeDeploy 흐름이 안정적으로 동작했다.
- 수동 운영 확인 명령은 자동 배포 환경변수 기준과 다를 수 있으므로 별도 기준이 필요하다.
- EC2에서 소스 동기화 상태를 `git status`, `git pull`로 관리하려 하면 배포 디렉터리와 충돌할 수 있으므로, 운영 상태 확인은 배포/컨테이너 기준으로 보는 것이 맞다.
- 현재 운영 디렉터리는 `.git` 없는 배포 경로로 유지하고, 코드 기준 확인은 GitHub 또는 별도 clone에서 수행하는 편이 맞다.

## 7. 결론

이번 단계에서 운영 배포는 `SSH 직접 실행` 중심 구조에서 `CodeDeploy / S3 / ECR` 중심 구조로 전환됐다.  
Blue/Green 전환 자체는 유지하면서, 빌드 책임을 EC2에서 GitHub Actions로 옮긴 것이 핵심 변화였다.
이와 함께 EC2는 더 이상 `git pull` 중심 배포 주체가 아니라, 배포 결과물을 받아 실행하는 운영 서버로 역할이 정리됐다.
현재 `/home/ubuntu/click-checker`는 실제로도 git 저장소가 아닌 배포 디렉터리 상태로 운영한다.

즉 이번 작업의 결과는 단순한 배포 도구 변경이 아니라, 운영 배포 구조를 한 단계 정리한 것이다.
