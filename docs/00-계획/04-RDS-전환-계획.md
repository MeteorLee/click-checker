# 04. RDS 전환 계획 (v1.0)

## 4단계 목표
- 운영 DB를 EC2 내부 PostgreSQL에서 Amazon RDS for PostgreSQL로 전환한다.
- 애플리케이션이 RDS 기준으로 정상 기동, Flyway 검증, smoke 테스트를 통과한다.
- 백업/복구/롤백 절차를 문서로 고정한다.

---

# 4.0 고정 원칙

## 1. 4단계는 기능 추가가 아니라 운영 안정화 단계다
- 범위는 DB 인프라 전환과 운영 절차 확립에 한정한다.
- 도메인 기능/API 스펙 변경은 포함하지 않는다.

## 2. 앱 롤백과 DB 복구는 분리한다
- 코드 롤백은 배포 단위로 처리한다.
- DB 복구는 백업/restore 또는 forward-fix migration으로 처리한다.

## 3. 운영 전환 전 리허설을 선행한다
- 운영 cutover 전에 리허설용 RDS에서 dump/restore/기동/smoke를 먼저 검증한다.

## 4. 전환 성공/실패 기준을 사전에 수치로 고정한다
- cutover 허용 다운타임: `10~20분`(초기 기준)
- 실패 기준: health 5분 내 `UP` 실패 또는 smoke 1건 실패 시 즉시 중단/복구

---

# 4.1 범위 정의

## 포함(in scope)
- RDS 인스턴스 생성
- 접속 정보/환경변수 분리
- 보안그룹/네트워크 연결 검증
- 운영 DB dump/restore 리허설
- Flyway `validate`/`migrate` 동작 확인
- health + smoke 확인
- 백업/복구/롤백 절차 문서화

## 제외(out of scope)
- 무중단 배포(Blue/Green, Canary)
- ALB/ECS 전환
- 읽기 복제본/멀티 AZ 고도화
- 고급 성능 튜닝/샤딩/파티셔닝

---

# 4.2 완료 기준 (Done)
- prod 앱이 RDS를 바라보고 정상 기동한다.
- Flyway `validate`/`migrate`가 RDS에서 정상 동작한다.
- `/actuator/health`가 `UP`이다.
- 이벤트 저장/조회 smoke 테스트가 통과한다.
- 전환 전후 핵심 데이터 검증이 완료된다.
  - 테이블 수
  - 핵심 row count(`organizations`, `users`, `events`)
  - `flyway_schema_history` 상태
- 백업/복구/롤백 문서가 확정된다.

---

# 4.3 실행 순서

## 1) 리허설 단계 (필수)
1. 리허설용 RDS 생성
2. 현재 운영 DB 백업(`pg_dump`)
3. 리허설 RDS restore
4. 앱 연결 후 Flyway/health/smoke 검증
5. 결과 기록(실패 시 원인/조치 포함)

## 2) 운영 전환 단계
1. 전환 시작 전 최종 백업
2. 운영 RDS 생성/설정 최종 점검
3. 최종 dump/restore
4. prod 앱 환경변수(RDS endpoint) 반영
5. 앱 기동 + Flyway 검증
6. health/smoke 통과 확인
7. 전환 완료 기록

---

# 4.4 체크포인트

## 네트워크/보안
- RDS `5432` inbound는 앱 EC2 Security Group만 허용
- `0.0.0.0/0` 허용 금지
- Public access 기본 `false`

## 애플리케이션 설정
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 분리 관리
- `ddl-auto=validate` 유지
- Flyway는 배포 루틴의 고정 단계로 실행

## 데이터 검증
- 전환 전후 테이블 수 비교
- 핵심 테이블 row count 비교
- 샘플 데이터 검증(`organizations/events/users`)
- `flyway_schema_history` 버전/성공 상태 확인

---

# 4.5 실패 대응

## 중단 조건
- health 5분 내 `UP` 미달
- smoke 실패
- 데이터 검증 불일치

## 대응 원칙
- 앱 실패: 이전 버전으로 코드 롤백
- DB 실패: 백업 restore 또는 forward-fix
- 중단 시점/원인/조치 내역을 운영 문서에 즉시 기록

