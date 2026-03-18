# 18. 조직 API Key 관리 계획 (v1.0)

## 목표
- 17단계에서 만든 `Account + Organization + OrganizationMember` 흐름 위에, 조직 자격증명(`X-API-Key`) 관리 경로를 올린다.
- 새 organization이 생성된 뒤 바로 이벤트 수집/분석 API를 사용할 수 있도록, 초기 API key 발급과 rotate 흐름을 설명 가능한 수준으로 정리한다.
- 기존 `X-API-Key` 인증 구조를 유지하되, 공개 조직 생성 경로가 아니라 `admin` 경로를 기준 흐름으로 재정렬한다.

---

# 18.0 고정 원칙

## 1. 이번 단계는 "조직 자격증명 1차"에 집중한다
- 이번 단계의 핵심은 organization이 실제로 이벤트 수집/분석 API를 사용할 수 있게 만드는 것이다.
- multi-key, key별 scope, 만료 정책, audit log까지 한 번에 확장하지 않는다.
- 즉 이번 단계는 "제품 자격증명 최소 관리" 단계다.

## 2. API Key는 계속 organization 단위 자격증명으로 본다
- `X-API-Key`는 계정 로그인 토큰이 아니다.
- API key는 여전히 organization scope를 결정하는 머신 인증 수단으로 유지한다.
- 계정/JWT와 API key는 같은 의미로 합치지 않는다.

## 3. organization당 활성 API key는 1개로 고정한다
- 1차 버전에서는 organization마다 활성 API key 1개만 둔다.
- 새 key 발급은 기존 key 교체(rotate)로 해석한다.
- 여러 key 동시 활성, key별 권한 분리는 후속 단계로 넘긴다.

## 4. plain API key는 생성/rotate 응답에서만 1회 노출한다
- DB에는 plain key를 저장하지 않는다.
- 평문 key는 조직 생성 직후 초기 발급 응답 또는 rotate 응답에서만 1회 노출한다.
- 메타데이터 조회 API에서는 `kid`, `prefix`, `status` 등 마스킹된 정보만 반환한다.

## 5. admin 경로를 기준 흐름으로 본다
- 포트폴리오 기준 기본 흐름은 아래로 고정한다.
  - `signup`
  - `POST /api/v1/admin/organizations`
  - organization 생성과 함께 초기 API key 1회 수령
- 기존 공개 `POST /api/organizations` 경로는 당장 제거하지 않더라도, 더 이상 주 경로로 설명하지 않는다.

## 6. role 정책은 최소로 시작한다
- organization 생성 직후의 초기 API key 발급은 creator `OWNER` 흐름 안에서 처리한다.
- API key 메타데이터 조회는 `OWNER`, `ADMIN`까지 허용할 수 있다.
- API key rotate는 `OWNER`만 가능하도록 시작한다.

## 7. 보안 원칙은 기존 정책을 유지한다
- API key 평문 미저장
- 로그 원문 미노출
- `kid` / `prefix` 중심 식별
- `ACTIVE` key만 인증 대상

---

# 18.1 범위 정의

## 포함(in scope)
- organization 생성 시 초기 API key 1회 발급
- organization의 현재 API key 메타데이터 조회 API
- organization API key rotate API
- old key 무효화 / new key 활성화 검증
- ingest / analytics API에서 새 key가 실제로 동작하는지 확인
- admin 경로 기준 API key 운영 정책 문서화

## 제외(out of scope)
- organization당 여러 API key
- key별 scope / 권한 분리
- API key 만료일 정책
- API key 이력 목록
- API key disable / enable UI
- audit log
- API key 발급 이메일/초대 흐름
- 공개 organization 생성 경로 즉시 제거

---

# 18.2 완료 기준 (Done)

- `POST /api/v1/admin/organizations` 성공 시 새 organization의 plain API key를 1회 받을 수 있다.
- `/api/v1/admin/organizations/{organizationId}/api-key`에서 현재 key의 메타데이터를 조회할 수 있다.
- `OWNER`는 `/api/v1/admin/organizations/{organizationId}/api-key/rotate`로 새 key를 발급할 수 있다.
- rotate 이후 old key는 `401`, new key는 이벤트 수집/분석 API에서 정상 동작한다.
- 메타데이터 조회 응답에서는 plain key가 다시 노출되지 않는다.
- 기존 `X-API-Key` 인증 정책과 새 admin API key 관리 경로가 문서와 코드에서 설명 가능하다.

---

# 18.3 현재 상태

## 이미 있는 것
- `organizations` 테이블에 단일 API key 관련 필드가 이미 있다.
  - `apiKeyKid`
  - `apiKeyHash`
  - `apiKeyPrefix`
  - `apiKeyStatus`
  - `apiKeyCreatedAt`
  - `apiKeyRotatedAt`
  - `apiKeyLastUsedAt`
- `ApiKeyIssuer`를 통해 plain key / `kid` / `prefix` / `hash` 발급이 가능하다.
- ingest / analytics API는 `X-API-Key -> authOrgId`로 organization scope를 결정한다.
- 17단계까지 `signup -> organization 생성 -> creator OWNER 연결` 흐름이 있다.

## 현재 구조의 한계
- 기존 plain key 발급은 공개 `POST /api/organizations` 경로에 묶여 있다.
- 새 admin organization 생성 경로는 owner membership은 만들지만, API key 발급은 아직 연결되지 않았다.
- 현재 key 메타 조회/rotate를 위한 admin API가 없다.
- 따라서 새 organization이 생겨도, 계정 기반 admin 흐름 안에서 바로 제품 사용 준비가 끝나지 않는다.

즉 이번 단계는 "조직 생성과 조직 자격증명 발급을 다시 연결하는 단계"다.

---

# 18.4 선행 확정 항목

## organization과 API key의 관계
- 1차 버전에서는 organization당 활성 API key 1개만 유지한다.
- `apiKeyStatus=ACTIVE`인 현재 key만 인증 대상이다.
- rotate는 "새 key 발급 + 기존 key 즉시 교체"로 본다.

## 초기 발급 방식
- `POST /api/v1/admin/organizations` 성공 응답에 초기 plain API key를 포함한다.
- 초기 발급 시 creator는 이미 해당 organization의 `OWNER` membership을 가진다.
- 즉 organization 생성과 초기 key 수령을 하나의 사용자 흐름으로 본다.

## 메타데이터 조회 정책
- plain key는 반환하지 않는다.
- 반환 대상:
  - `kid`
  - `prefix`
  - `status`
  - `createdAt`
  - `rotatedAt`
  - `lastUsedAt`
- 메타 조회는 `OWNER`, `ADMIN`까지 허용 가능하다.

## rotate 정책
- `OWNER`만 rotate 가능
- rotate 성공 시:
  - 새 plain key 1회 반환
  - `kid/hash/prefix` 갱신
  - `status=ACTIVE`
  - `rotatedAt` 갱신
- rotate 이후 old key는 즉시 인증 실패해야 한다.

## 공개 경로와의 관계
- 기존 `POST /api/organizations`는 당장 제거하지 않는다.
- 다만 포트폴리오 기준 주 경로는 `POST /api/v1/admin/organizations`로 옮긴다.
- 공개 경로 제거/정리 시점은 후속 정리 작업으로 둔다.

## 에러 정책
- 인증 실패: `401`
- organization 없음: `404`
- membership 없음: `403`
- role 부족: `403`
- rotate 요청 형식 오류: `400`

---

# 18.5 도메인 설계

## 1) Organization의 API key 필드 재사용
- 이번 단계에서는 별도 `OrganizationApiKey` 엔티티를 만들지 않는다.
- 기존 `Organization`의 단일 key 필드를 그대로 사용한다.
- 즉 이번 단계는 새 도메인을 늘리기보다, 기존 모델을 admin 흐름에 연결하는 단계다.

## 2) ApiKeyIssuer
- 기존 `ApiKeyIssuer`를 그대로 사용한다.
- 책임:
  - plain key 발급
  - `kid` 생성
  - `prefix` 생성
  - hash 생성

## 3) ApiKeyStatus
- 1차 버전에서는 사실상 `ACTIVE` 중심으로 사용한다.
- disable/enable 운영은 후속 단계로 둔다.
- 다만 메타데이터와 기존 인증 정책 설명을 위해 `status` 필드는 유지한다.

---

# 18.6 API 설계

## 1) organization 생성 + 초기 API key 발급
- `POST /api/v1/admin/organizations`
- 권한:
  - 로그인된 account
- 동작:
  - 새 `Organization` 생성
  - creator account를 첫 `OWNER` membership으로 연결
  - 초기 plain API key 발급
- 응답:
  - `organizationId`
  - `organizationName`
  - `apiKey`
  - `apiKeyPrefix`

## 2) 현재 API key 메타데이터 조회
- `GET /api/v1/admin/organizations/{organizationId}/api-key`
- 권한:
  - `OWNER`, `ADMIN`
- 응답:
  - `kid`
  - `prefix`
  - `status`
  - `createdAt`
  - `rotatedAt`
  - `lastUsedAt`

예시:
```json
{
  "kid": "2c4f0c8b7c9d...",
  "prefix": "2c4f0c8b",
  "status": "ACTIVE",
  "createdAt": "2026-03-18T10:00:00Z",
  "rotatedAt": "2026-03-18T10:00:00Z",
  "lastUsedAt": "2026-03-18T10:05:12Z"
}
```

## 3) API key rotate
- `POST /api/v1/admin/organizations/{organizationId}/api-key/rotate`
- 권한:
  - `OWNER`
- 동작:
  - 새 key 발급
  - organization의 `kid/hash/prefix` 교체
  - old key 즉시 무효화
- 응답:
  - `apiKey`
  - `apiKeyPrefix`
  - `rotatedAt`

---

# 18.7 보안 / 운영 설계

## 로그 정책
- plain API key는 로그에 남기지 않는다.
- 요청/응답 로그에서도 생성/rotate 응답의 `apiKey`는 마스킹 대상이다.
- 운영 로그 식별자는 `kid` 또는 `prefix`만 사용한다.

## 배포 / smoke 관점
- 새 organization 생성 후 받은 API key로 write/read smoke가 가능해야 한다.
- rotate 이후에는 old key 실패 / new key 성공을 smoke 또는 통합테스트로 검증한다.

## 문서 관점
- `X-API-Key` 인증 정책 문서와 account/JWT 문서를 분리 유지한다.
- 이번 단계에서는 "조직 자격증명"과 "계정 로그인" 경계를 더 분명히 보여줘야 한다.

---

# 18.8 실행 순서

## 1) 현재 구조 연결
1. admin organization 생성 서비스에 초기 API key 발급 연결
2. 기존 공개 organization 생성 흐름과 책임 겹침 지점 확인

## 2) 조회/rotate API 추가
1. API key 메타 조회 API
2. rotate API
3. role 검증 연결

## 3) 검증
1. organization 생성 시 초기 key 수령
2. key로 이벤트 수집 성공
3. key로 분석 조회 성공
4. rotate 이후 old key 실패
5. rotate 이후 new key 성공

---

# 18.9 체크포인트

## 보안
- plain key가 DB에 저장되지 않는지
- 메타 조회에서 plain key가 다시 노출되지 않는지
- 로그에 원문 key가 남지 않는지

## 권한
- 메타 조회는 `OWNER`, `ADMIN`만 가능한지
- rotate는 `OWNER`만 가능한지
- membership 없는 계정은 `403`인지

## 제품 흐름
- `signup -> organization 생성 -> API key 수령 -> ingest/read`
  흐름이 한 번에 설명 가능한지

---

# 18.10 후속 작업
- organization당 여러 API key 지원
- key별 scope / 권한 분리
- API key disable / enable
- API key 이력 목록
- admin 콘솔의 key 관리 화면
- 공개 `POST /api/organizations` 경로 정리 또는 제거
