# Logging Policy (0.3 최소 기준)

## 1. 목적
- 장애 분석에 필요한 로그는 남기되, 민감정보 유출은 방지한다.

## 2. 절대 로그 금지 항목
- `Authorization` 헤더 전체
- `Cookie` 헤더 전체
- `X-API-Key` / `apiKey` 원문
- Access Token / Refresh Token / JWT 원문
- 비밀번호/시크릿/서명값
- 개인정보 원문(이메일, 전화번호 등 식별 가능한 값)

## 3. 허용 항목 (기본)
- `requestId`
- HTTP method, path, status
- latency
- organizationId (테넌트 식별 목적)

## 4. 개발 규칙
- 요청/응답 원문(body) 전체 로그 금지
- 필요한 필드만 allowlist 방식으로 선택 로그
- 민감 가능 필드는 마스킹 후 로그
- 신규 로그 추가 시 "민감정보 포함 여부"를 리뷰 체크

## 5. 마스킹 원칙
- 토큰류: `****`
- 긴 식별자: 앞/뒤 일부만 노출 (예: `ab***yz`)
- 외부 사용자 식별자: 가능하면 해시 사용

## 6. 현재 프로젝트 적용 상태
- requestId 생성/전파 + MDC 연결 완료
- 로그 패턴에서 requestId 출력 가능
- 공통 기본값에서 request 상세 로그 비활성화
- local 프로파일은 관찰 목적 설정을 유지
- 코드 적용 위치:
  - requestId 필터: `src/main/java/com/clickchecker/web/filter/RequestIdFilter.java`
  - 마스킹 유틸: `src/main/java/com/clickchecker/logging/LogMaskingUtil.java`

## 7. 다음 보강
- 공통 마스킹 유틸(또는 로깅 헬퍼) 도입
- 에러 로그 포맷 표준화
- Sentry 도입 시 동일 차단 정책 적용
