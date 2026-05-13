# 프런트엔드 진입 및 same-origin 정책

## 목적
- frontend와 backend를 한 도메인 아래에서 어떻게 나누는지 짧게 고정한다.
- 브라우저에서 보이는 경로와 내부 포트의 역할을 구분한다.

## 현재 public entrypoint
- 외부 사용자는 `https://clickchecker.dev` 하나만 사용한다.
- 브라우저 페이지는 frontend가 응답한다.
- `/api/**`, `/actuator/health`, `/actuator/health/readiness`, `/swagger-ui/**`, `/v3/api-docs/**`는 backend가 응답한다.

## 내부 포트
- frontend: `127.0.0.1:3001 -> container:3000`
- backend:
  - `127.0.0.1:8081`
  - `127.0.0.1:8082`

## 브라우저 기준 원칙
- 운영에서 브라우저는 내부 포트로 직접 붙지 않는다.
- frontend는 제품 API와 admin API를 same-origin 상대경로(`/api/...`)로 호출한다.
- 따라서 운영 확인은 `clickchecker.dev` 기준으로만 본다.

## 로컬 개발 기준
- backend: `http://localhost:8080`
- frontend dev server: `http://localhost:3001`
- 로컬 nginx 확인용: `http://localhost:18080`

## 왜 필요한가
- 운영에서는 CORS와 도메인 혼선을 줄인다.
- 브라우저는 한 도메인만 보게 하고, nginx가 내부에서 frontend/backend를 분기한다.
- local dev와 운영 배포의 API 진입 구조가 다르다는 점을 문서로 고정한다.
