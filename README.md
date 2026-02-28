# Click Checker

이 프로젝트는 외부 서비스에서 발생하는 이벤트(클릭, 조회 등)를 수집하고  
일정 시간 단위로 집계하여 지표를 제공하는 백엔드 서버입니다.

## Goal
- 이벤트 수집 API 설계
- 시간 단위 집계 구조 이해
- 트래픽 증가 상황을 고려한 서버 설계 연습

## Tech Stack
- Language: Java 21
- Framework: Spring Boot 3
- DB: PostgreSQL (Docker)
- Build/CI: Gradle, GitHub Actions (JDK 21)
- CI에서는 ci 프로필로 H2를 사용해 contextLoads가 통과하는 상태
- ORM/Query: Spring Data JPA + Querydsl
- Mapper: MapStruct
- Dev env: WSL (Ubuntu)
