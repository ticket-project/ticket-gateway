# Ticket Gateway 저장소 Copilot 지침

이 저장소에서 Copilot Chat, Copilot code review, Copilot coding agent는 루트 [AGENTS.md](../AGENTS.md)를 공통 기준으로 따른다.

## 문서 우선순위

1. `AGENTS.md`
2. `README.md`
3. `build.gradle`, `settings.gradle`
4. `src/main/resources/application.yml`
5. 테스트 코드

## Copilot 전용 리뷰 기준

- 모든 리뷰, 제안, 설명은 한국어로 작성한다.
- 패치만 보지 말고 주변 코드, 관련 설정, 관련 테스트, 라우팅 흐름을 함께 본다.
- 요청 범위를 벗어난 기능 추가, 리팩터링, 추상화는 제안하지 않는다.
- 스타일 취향보다 실제 결함 가능성, 회귀 위험, 테스트 공백을 우선 본다.

## 우선 검토 영역

- Spring Cloud Gateway MVC 라우팅 순서와 predicate/filter 적용 범위
- JWT 검증, 내부 인증 토큰 전파, 공개 라우트와 인증 라우트 경계
- CORS, 보안 헤더, actuator 노출 범위
- 백엔드 URI와 운영 환경 변수
- route 변경에 따른 기존 API 호환성

## 권장 검증

- 빠른 검증: `./gradlew test`
- 회귀 확인: `./gradlew test --rerun-tasks`
