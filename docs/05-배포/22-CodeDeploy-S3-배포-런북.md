# 22. CodeDeploy / S3 배포 런북

## 문서 목적

현재 운영 배포를 `GitHub Actions -> S3 -> CodeDeploy -> EC2` 구조로 전환하는 과정과 실행 기준을 정리한다.  
이번 작업에서는 기존 Blue/Green 전환 로직을 유지한 채, 운영 서버에서 직접 빌드하던 흐름을 ECR 이미지 기반 배포로 바꾸는 것을 목표로 한다.

## 현재 전제

- 운영 구조는 단일 EC2 + Nginx + RDS + `app-blue/app-green` 기준이다.
- 현재 운영 전환 스크립트는 이미 정리돼 있다.
  - `scripts/deploy-prod-blue-green.sh`
  - `scripts/blue-green-prod-switch.sh`
- 현재 운영 배포는 CodeDeploy / S3 / ECR 기준으로 성공한 상태다.
- 이번 단계에서는 기존 SSH 배포 기록은 남기되, 운영 기준은 CodeDeploy 흐름으로 본다.

## 이번 런북의 범위

- CodeDeploy 기본 파일 구조 작성
- `appspec.yml` 기준 확정
- CodeDeploy hook 스크립트 역할 정리
- GitHub Actions의 S3 / CodeDeploy / ECR 호출 구조 반영
- 운영 검증 기준 정의

## 목표

- 저장소 안에 CodeDeploy가 읽는 기본 파일과 hook 스크립트를 정리한다.
- GitHub Actions가 `bootJar`, Docker image build, ECR push, S3 업로드, CodeDeploy 배포 생성까지 수행하게 만든다.
- EC2는 ECR 이미지 pull 후 기존 Blue/Green 전환 스크립트를 실행하게 만든다.
- 실패 시 기존 Blue/Green 복구 기준을 그대로 유지한다.

## 사전 체크리스트

- 현재 운영 기본 색상 확인
- `clickchecker.dev`, `/actuator/health` 정상 확인
- EC2에 CodeDeploy Agent 설치 및 실행 상태 확인
- EC2 IAM Role의 S3 / ECR 접근 권한 확인
- GitHub Actions 역할의 S3 / CodeDeploy / ECR 접근 권한 확인
- 기존 Blue/Green 배포 스크립트 위치와 역할 확인

## 적용 순서

### 1. CodeDeploy 계획 기준 확인

기준 문서:

```text
docs/00-계획/10-codedeploy-s3-배포-계획.md
```

확인 포인트:

- 이번 단계가 배포 전달 방식 변경 단계인지
- Blue/Green 전환 로직은 그대로 유지하는지
- SSH 배포를 fallback으로 남기는지

### 2. 저장소 기본 파일 정리

다음 파일을 기준으로 CodeDeploy 구조를 정리한다.

- `appspec.yml`
- `scripts/codedeploy/after-install.sh`
- `scripts/codedeploy/application-start.sh`
- `scripts/codedeploy/validate-service.sh`

원칙:

- hook 스크립트는 기존 배포 스크립트 역할을 중복 구현하지 않는다.
- 가능하면 기존 `deploy-prod-blue-green.sh`를 호출하는 구조로 유지한다.
- 다만 운영 서버에서 Docker build를 수행하지 않도록 이미지 전달 방식은 변경한다.

### 3. CodeDeploy hook 역할 정리

최종 역할:

- `AfterInstall`
  - 실행 권한 정리
  - 필수 파일 존재 확인
  - 배포 대상 이미지 정보 정리
- `ApplicationStart`
  - ECR 로그인
  - 대상 이미지 pull
  - 기존 배포 스크립트 실행
- `ValidateService`
  - 공개 health 기준 재검증

주의:

- 이번 단계에서는 CodeDeploy hook 안에 Blue/Green 전환 로직을 새로 넣지 않는다.
- 전달 방식과 실행 순서만 정리한다.

### 4. GitHub Actions 전달 구조 정리

최종 적용 흐름:

```text
GitHub Actions
-> bootJar
-> Docker image build
-> ECR push
-> 배포본 zip 생성
-> S3 업로드
-> CodeDeploy deployment 생성
-> EC2에서 hook 실행
-> ECR image pull
-> Blue/Green 전환
```

핵심 기준:

- 운영 서버에서 Docker build를 하지 않는다.
- GitHub Actions가 배포 대상 이미지를 먼저 만든다.
- EC2는 전달된 이미지 정보를 기준으로 pull 후 실행한다.

### 5. 운영 검증 기준 고정

확인 포인트:

- 배포 후 `/actuator/health = UP`
- smoke 통과
- 공개 경로 응답 정상
- ECR 이미지 기준으로 `app-blue` 또는 `app-green` 실행 확인
- 필요 시 기존 색상으로 복구 가능

## 롤백 기준

다음 상황이면 배포 실패로 판단하고 기존 활성 색상 유지 또는 복구가 가능해야 한다.

- CodeDeploy 전달 구조가 완성되지 않음
- hook 실행 실패
- health 실패
- smoke 실패
- 운영 전환 후 공개 응답 이상

원칙:

- CodeDeploy 구조 안에서 먼저 복구를 시도하고, 필요 시 기존 수동 운영 기준으로 확인한다.

## 전환 후 확인 항목

- CodeDeploy 기본 파일이 저장소 안에 존재한다.
- hook 역할이 문서와 코드 기준으로 일치한다.
- GitHub Actions가 `S3 + CodeDeploy + ECR` 흐름으로 동작한다.
- EC2가 ECR 이미지 pull 후 Blue/Green 전환을 수행한다.
- 운영 서버 빌드 없이 실제 배포 성공 이력이 남는다.

## 다음 단계

- 실제 작업 과정에서 발생한 문제는 트러블슈팅 문서에 누적한다.
- 구조와 운영 검증 결과는 종합 문서에서 정리한다.
- 이후에는 CodeDeploy 배포 후 운영 확인 명령과 문서 톤을 정리한다.
