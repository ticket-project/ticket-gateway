---
applyTo: "src/**/*.java"
---

# Java/Spring 리뷰 강화 지침

- Java 변경은 패치만 보지 말고 관련 route config, filter, controller, config, test까지 함께 확인한다.
- Spring Cloud Gateway MVC 라우팅 순서, predicate/filter 적용 범위, URI 설정을 우선 검토한다.
- JWT 검증, 내부 인증 토큰 전파, 공개 API와 보호 API의 경계가 깨지지 않는지 확인한다.
- CORS, 헤더 전달, actuator 노출은 보안과 운영 장애 관점에서 본다.
- 테스트가 없다면 어떤 테스트가 빠졌는지 구체적으로 적는다.
