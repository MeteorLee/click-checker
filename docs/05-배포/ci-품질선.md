# CI 품질선 (1.2)

## 0. 목적
- CI를 배포 전 최소 게이트로 사용한다.
- CI 실패 시 배포는 진행하지 않는다.

## 1. 현재 품질선 (즉시 적용)
- GitHub Actions `ci.yml`에서 `./gradlew test`를 실행한다.
- CI 프로파일(`SPRING_PROFILES_ACTIVE=ci`)로 테스트를 수행한다.
- 테스트 실패 시 워크플로우 실패로 처리한다.

## 2. 배포 차단 원칙
- `build/test` 단계 중 하나라도 실패하면 배포 단계로 진행하지 않는다.
- 경고(warning)는 기록하되, 배포 차단 기준은 “테스트 실패”를 우선 적용한다.

## 3. 핵심 시나리오 기준
- 최소 보장 시나리오:
  1. 이벤트 수집 요청이 저장까지 성공한다.
  2. 집계 조회 요청이 정상 응답한다.
- 위 시나리오는 통합테스트로 유지하고, CI에서 항상 실행한다.

## 4. 현재 리스크
- CI는 H2 기반이므로 PostgreSQL 전용 이슈를 놓칠 수 있다.
- Querydsl/SQL 함수의 DB 의존 구문은 운영 DB에서만 재현될 가능성이 있다.

## 5. 다음 보강 (1.2 PATCH)
- PostgreSQL 기반 통합 테스트 최소 1개 추가(적용 완료)
  - 적용 방식: Testcontainers
  - 적용 테스트: `EventQueryControllerPostgresIntegrationTest`
  - CI job: `postgres-compat`
- 추가 확장 옵션(후속):
  - docker-compose 기반 CI job
  - nightly 검증 job
- 선택 기준:
  - 구현 복잡도
  - CI 실행 시간
  - 재현성/안정성

## 6. 완료 기준
- CI 실패 시 배포가 차단된다.
- 핵심 시나리오 테스트가 CI에서 상시 통과한다.
- PostgreSQL 전용 리스크를 다루는 최소 검증 경로가 1개 이상 존재한다.

## 7. 현재 상태 요약
- 적용됨:
  - `build` job: `./gradlew test` (ci 프로파일)
  - `postgres-compat` job: PostgreSQL 호환성 테스트 1개 실행
- 남음:
  - Postgres 검증 범위 확대(집계 외 시나리오)
  - 공통 에러 응답 포맷 + 글로벌 예외 처리 골격 정리
