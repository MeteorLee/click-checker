# 22. CodeDeploy / S3 배포 런북

## 문서 목적

현재 `GitHub Actions -> SSH -> EC2` 배포를 `GitHub Actions -> S3 -> CodeDeploy -> EC2` 구조로 옮기기 위한 실행 절차를 정리한다.  
이 단계에서는 기존 Blue/Green 전환 로직을 유지한 채, 배포 전달 방식만 바꾸는 것을 목표로 한다.

## 현재 전제

- 운영 구조는 단일 EC2 + Nginx + RDS + `app-blue/app-green` 기준이다.
- 현재 운영 전환 스크립트는 이미 정리돼 있다.
  - `scripts/deploy-prod-blue-green.sh`
  - `scripts/blue-green-prod-switch.sh`
- 현재 자동 배포는 GitHub Actions가 EC2에 SSH 접속해 스크립트를 실행하는 방식이다.
- 이번 단계에서는 SSH 배포를 즉시 제거하지 않고 fallback으로 유지한다.

## 이번 런북의 범위

- CodeDeploy 기본 파일 구조 작성
- `appspec.yml` 기준 확정
- CodeDeploy hook 스크립트 역할 정리
- GitHub Actions의 S3 / CodeDeploy 호출 구조 준비
- 운영 검증 기준 정의

## 목표

- 저장소 안에 CodeDeploy가 읽을 기본 파일을 만든다.
- CodeDeploy가 기존 Blue/Green 배포 스크립트를 호출할 수 있게 연결한다.
- GitHub Actions가 배포본 zip 생성, S3 업로드, CodeDeploy 배포 생성 구조로 전환할 준비를 마친다.
- 실패 시 기존 복구 기준을 그대로 유지한다.

## 사전 체크리스트

- 현재 SSH 배포가 정상 동작 중인지 확인
- 현재 운영 기본 색상 확인
- `clickchecker.dev`, `/actuator/health` 정상 확인
- EC2에 CodeDeploy Agent 설치 가능 여부 확인
- EC2 IAM Role의 S3 / CodeDeploy 접근 권한 확인 가능 여부 확인
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

### 2. 저장소 기본 파일 작성

우선 다음 파일을 준비한다.

- `appspec.yml`
- `scripts/codedeploy/after-install.sh`
- `scripts/codedeploy/application-start.sh`
- `scripts/codedeploy/validate-service.sh`

원칙:

- hook 스크립트는 기존 배포 스크립트 역할을 중복 구현하지 않는다.
- 가능하면 기존 `deploy-prod-blue-green.sh`를 호출하는 구조로 유지한다.

### 3. CodeDeploy hook 역할 정리

기준 역할:

- `AfterInstall`
  - 실행 권한 정리
  - 필수 파일 존재 확인
- `ApplicationStart`
  - 기존 배포 스크립트 실행
- `ValidateService`
  - health / smoke 기준 재검증

주의:

- 이번 단계에서는 CodeDeploy hook 안에 Blue/Green 전환 로직을 새로 넣지 않는다.
- 전달 방식과 실행 순서만 정리한다.

### 4. GitHub Actions 전달 구조 정리

최종 목표 흐름:

```text
GitHub Actions
-> 배포본 zip 생성
-> S3 업로드
-> CodeDeploy deployment 생성
-> EC2에서 hook 실행
```

현재 단계에서는:

- 배포본 생성 범위
- 업로드 대상
- CodeDeploy 호출 방식
을 먼저 정리한다.

### 5. 운영 검증 기준 고정

확인 포인트:

- 배포 후 `/actuator/health = UP`
- smoke 통과
- 공개 경로 응답 정상
- 필요 시 기존 색상으로 복구 가능

## 롤백 기준

다음 상황이면 기존 SSH 배포 기준으로 즉시 되돌릴 수 있어야 한다.

- CodeDeploy 전달 구조가 완성되지 않음
- hook 실행 실패
- health 실패
- smoke 실패
- 운영 전환 후 공개 응답 이상

원칙:

- CodeDeploy 구조가 안정화되기 전까지는 기존 SSH 배포를 fallback으로 유지한다.

## 전환 후 확인 항목

- CodeDeploy 기본 파일이 저장소 안에 존재한다.
- hook 역할이 문서와 코드 기준으로 일치한다.
- 기존 배포 스크립트와 충돌 없이 연결된다.
- GitHub Actions가 이후 S3 / CodeDeploy 흐름으로 전환 가능한 상태가 된다.

## 다음 단계

- 실제 작업 과정에서 발생한 문제는 트러블슈팅 문서에 누적한다.
- 구조가 안정화되면 종합 문서에서 최종 배포 흐름을 정리한다.
- 최종적으로 GitHub Actions를 S3 / CodeDeploy 기준으로 전환한다.
