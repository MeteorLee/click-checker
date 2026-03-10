# 10. CodeDeploy / S3 배포 계획 (v1.0)

## 목표
- 현재 `GitHub Actions -> SSH -> EC2` 배포를 `GitHub Actions -> S3 -> CodeDeploy -> EC2` 구조로 전환한다.
- 기존 Blue/Green 전환 구조는 유지한 채, 배포 전달 방식만 정리한다.
- 현재 운영 구조를 깨뜨리지 않는 전환 순서를 먼저 고정한다.

---

# 10.0 고정 원칙

## 1. 이번 단계는 전달 방식 변경에 집중한다
- 핵심은 Blue/Green 전환 로직을 새로 만드는 것이 아니다.
- 현재 배포/전환 로직은 이미 정리된 스크립트를 기준으로 유지한다.

## 2. 기존 운영 구조를 그대로 전제로 한다
- 현재 운영 구조는 단일 EC2 + Nginx + RDS + app-blue/app-green 기준이다.
- 이번 단계는 이 구조를 바꾸지 않고 전달 경로만 바꾼다.

## 3. 저장소 구조를 먼저 만든다
- AWS 콘솔 설정부터 시작하지 않는다.
- 먼저 저장소 안에 CodeDeploy가 읽을 파일과 실행 순서를 만든다.

## 4. SSH 배포는 즉시 제거하지 않는다
- CodeDeploy 흐름이 실제로 안정적으로 동작하기 전까지는 현재 SSH 배포를 fallback으로 유지한다.

## 5. 실패 기준은 기존 배포 기준을 그대로 따른다
- health 실패
- smoke 실패
- 전환 후 공개 응답 이상
- 위 조건 중 하나라도 발생하면 이전 안정 상태로 복구 가능해야 한다.

---

# 10.1 범위 정의

## 포함(in scope)
- `appspec.yml` 작성
- CodeDeploy hook 스크립트 구조 정리
- GitHub Actions 기준 배포본 생성 구조 정리
- S3 업로드 및 CodeDeploy 배포 호출 흐름 정의
- 기존 Blue/Green 스크립트와 CodeDeploy 연결 방식 정리

## 제외(out of scope)
- ALB / 멀티 EC2
- Auto Scaling Group
- CodeDeploy의 Blue/Green 배포 그룹 기능 사용
- 운영 아키텍처 전체 변경

---

# 10.2 완료 기준 (Done)

- 저장소 안에 CodeDeploy 기본 파일이 존재한다.
- CodeDeploy hook 역할이 분명하다.
- 기존 Blue/Green 배포 스크립트를 CodeDeploy 흐름에서 호출할 수 있다.
- GitHub Actions가 SSH 대신 S3 / CodeDeploy 호출 구조로 전환할 준비가 된다.

---

# 10.3 실행 순서

## 1) 저장소 구조 준비
1. `appspec.yml` 작성
2. `scripts/codedeploy/` 디렉터리 구성
3. hook 파일 이름과 역할 정의

## 2) hook 스크립트 구조 작성
1. `AfterInstall`
   - 파일 권한 정리
   - 필수 파일 존재 확인
2. `ApplicationStart`
   - 기존 배포 스크립트 실행
3. `ValidateService`
   - health / smoke 기준 재검증

## 3) GitHub Actions 전달 구조 전환
1. 배포본 zip 생성
2. S3 업로드
3. CodeDeploy deployment 생성
4. 배포 결과 확인

## 4) 운영 검증
1. 실제 배포 실행
2. 로그 확인
3. health / smoke / 색상 전환 확인
4. 실패 시 복구 경로 확인

---

# 10.4 체크포인트

## 저장소
- CodeDeploy가 읽을 파일 이름과 위치가 명확한지
- hook 스크립트와 기존 배포 스크립트 역할이 충돌하지 않는지

## GitHub Actions
- 배포본 생성 범위가 너무 크지 않은지
- SSH 직접 실행 대신 S3 / CodeDeploy 호출 구조로 정리되는지

## 운영
- EC2에 CodeDeploy Agent가 설치돼 있는지
- EC2 IAM Role이 S3 / CodeDeploy 접근 권한을 가지는지
- 배포 실패 시 기존 복구 기준이 유지되는지

---

# 10.5 후속 작업
- 실제 저장소 구성 결과를 실행기록 문서에 반영
- GitHub Actions 배포 경로를 S3 / CodeDeploy 구조와 연결
- 필요 시 운영 검증 결과를 런북 / 트러블슈팅 문서에 반영
