# Ticket Gateway

기준일: 2026-05-31

Ticket 시스템의 단일 HTTP/WebSocket 진입점이다. Spring Cloud Gateway Server MVC 기반으로 Frontend 요청을 Ticket Server와 Queue Server로 라우팅하고, 외부 사용자 JWT(Access Token)를 검증해 downstream 신뢰용 내부 인증 토큰(Internal Auth Token)으로 교환한다.

## 빠른 맥락

- 애플리케이션: Spring Boot Gateway (Spring Cloud Gateway Server MVC, 서블릿 기반)
- 기본 포트: `8000`
- Ticket Server 기본 대상: `http://localhost:8080`
- Queue Server 기본 대상: `http://localhost:8090`
- Frontend 기본 origin: `http://localhost:3000`
- **인증(authentication)** 은 Gateway가 수행한다. 사용자 Access Token을 검증하고 downstream으로는 audience별 Internal Auth Token을 발급해 전달한다.
- **인가(authorization)** 와 그 외 도메인 규칙은 각 backend(Ticket Server, Queue Server)가 수행한다.

## 라우팅

라우트는 `GatewayRoutesConfig`에 Java DSL(`RouterFunction`)로 정의되어 있고, 순서대로 평가된다.

| 우선순위 | External path | Internal target | 설명 |
| --- | --- | --- | --- |
| 1 | `/api/v1/queue/**` | Queue Server | 대기열 진입, 상태 조회 |
| 2 | `/ws/**` | Ticket Server WebSocket | 좌석 상태 실시간 전파 |
| 3 | `/api/**` | Ticket Server REST API | 인증, 공연 조회, 좌석 선택, 주문 |

`/api/v1/queue/**`는 반드시 일반 `/api/**`보다 먼저 매칭되어야 한다(통합 테스트가 이를 보장).

## 인증 처리

`GatewayAuthFilter`(`OncePerRequestFilter`)가 다음과 같이 동작한다.

1. CORS preflight(`OPTIONS`)와 Gateway 대상이 아닌 경로는 통과시킨다.
2. 어떤 경로든 클라이언트가 보낸 `X-Internal-Auth` 헤더는 항상 제거한다(스푸핑 방지).
3. 라우트 대상이 `/api/v1/queue/**`이면 `QUEUE`, 그 외는 `CORE`로 분류한다.
4. 공개(public) 경로면 인증 없이 그대로 통과시킨다.
5. 보호 경로면 `Authorization: Bearer <accessToken>`을 검증하고, 성공 시 audience를 분리한 Internal Auth Token을 발급해 `X-Internal-Auth: Bearer <internalToken>` 헤더로 교체한다. 실패/누락 시 `401`을 응답한다.

### 인증 면제(public) 경로

- 무조건: `/`, `/api/swagger-ui*`, `/api/api-docs/**`, `/ws/**`, `/api/images/**`, `/actuator/health(/**)`, `/actuator/info`, `/actuator/prometheus`
- 인증 관련 공개 엔드포인트: `/api/v1/auth/signup`, `/login`, `/refresh`, `/oauth2/token`, `/social/urls`, `/oauth2/authorize/**`, `/oauth2/callback/*`
- GET 전용 공개 조회: `/api/v1/shows(/**)`, `/api/v1/performances(/**)`, `/api/v1/genres(/**)`, `/api/v1/meta(/**)`
- Queue 영역: 대부분 면제. 단 `POST /api/v1/queue/performances/*/enter`는 보호 대상이다.

### Internal Auth Token 발급 규칙

| 라우트 대상 | issuer | audience | 수명 |
| --- | --- | --- | --- |
| Ticket Server (`CORE`) | `ticket-gateway` | `ticket-core` | 60초 |
| Queue Server (`QUEUE`) | `ticket-gateway` | `ticket-queue` | 60초 |

같은 secret이지만 audience만 다른 두 발급기를 사용하므로, downstream은 자신의 audience로만 토큰을 검증한다.

### 헤더 처리 요약

| 헤더 | 동작 |
| --- | --- |
| `Authorization` | downstream까지 그대로 전달 |
| `X-Queue-Session` | downstream까지 그대로 전달 |
| `X-Admission-Token` | downstream까지 그대로 전달 |
| `X-Internal-Auth` | 클라이언트 값은 항상 제거. 인증 성공 시 Gateway가 새로 발급해 주입 |

## 로컬 실행

전제:

- JDK 25
- Ticket Server가 `8080`에서 실행 중이거나 `TICKET_API_URI`로 대상 지정
- Queue Server가 `8090`에서 실행 중이거나 `TICKET_QUEUE_URI`로 대상 지정

```powershell
.\gradlew.bat bootRun
```

다른 대상 서버를 쓰는 경우:

```powershell
$env:GATEWAY_PORT="8000"
$env:TICKET_API_URI="http://localhost:8080"
$env:TICKET_API_WS_URI="http://localhost:8080"
$env:TICKET_QUEUE_URI="http://localhost:8090"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:3000"

.\gradlew.bat bootRun
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `GATEWAY_PORT` | `8000` | Gateway listen port |
| `TICKET_API_URI` | `http://localhost:8080` | Ticket Server REST target |
| `TICKET_API_WS_URI` | `http://localhost:8080` | Ticket Server WebSocket target |
| `TICKET_QUEUE_URI` | `http://localhost:8090` | Queue Server target |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 허용할 frontend origin |
| `JWT_ISSUER` | `ticket` | 사용자 Access Token issuer (검증 시 일치 강제) |
| `JWT_SECRET` | (개발용 32바이트 placeholder) | Access Token HS256 secret. **운영에서는 반드시 override** |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `1800` | Access Token TTL(검증 측에는 영향 없음) |
| `INTERNAL_AUTH_ISSUER` | `ticket-gateway` | Internal Auth Token issuer |
| `INTERNAL_AUTH_SECRET_KEY` | (개발용 32바이트 placeholder) | Internal Auth Token HS256 secret. **운영에서는 반드시 override** |
| `INTERNAL_AUTH_EXPIRATION_SECONDS` | `60` | Internal Auth Token TTL |

> 모든 HS256 secret은 32바이트 이상이어야 한다. 기동 시 fail-fast 검증이 동작한다.

## 구조

```text
ticket-gateway
├── src/main/java/com/ticket/gateway
│   ├── TicketGatewayApplication.java
│   └── config
│       ├── GatewayRoutesConfig.java            # route 정의 (RouterFunction)
│       ├── GatewayBackendProperties.java       # downstream URI 설정
│       ├── GatewayCorsConfig.java              # CORS 설정 (CorsFilter Bean)
│       ├── GatewayCorsProperties.java          # 허용 origin 설정
│       ├── GatewayAuthConfig.java              # 인증 필터/토큰 서비스 조립
│       ├── GatewayAuthFilter.java              # 핵심 인증 필터 (JWT 검증 + 토큰 교환)
│       ├── GatewayJwtProperties.java           # 사용자 Access Token 설정
│       └── GatewayInternalAuthProperties.java  # Internal Auth Token 설정
└── src/main/resources/application.yml
```

## 핵심 파일

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/ticket/gateway/TicketGatewayApplication.java` | 실행 진입점 |
| `src/main/java/com/ticket/gateway/config/GatewayRoutesConfig.java` | `/api/v1/queue/**`, `/ws/**`, `/api/**` route 정의 |
| `src/main/java/com/ticket/gateway/config/GatewayAuthFilter.java` | JWT 검증 + Internal Auth Token 발급 + `X-Internal-Auth` 헤더 제어 |
| `src/main/java/com/ticket/gateway/config/GatewayAuthConfig.java` | `JwtTokenVerifier`, audience별 `InternalAuthTokenService` 빈 구성 |
| `src/main/java/com/ticket/gateway/config/GatewayCorsConfig.java` | CORS 정책 |
| `src/main/resources/application.yml` | 포트, backend URI, secret, actuator 설정 |

## 검증 명령

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

라우팅/인증 변경 시 최소 확인:

- `/api/v1/queue/**`가 Queue Server로 가는지
- `/api/**`가 Ticket Server REST로 가는지
- `/ws/**`가 Ticket Server WebSocket으로 가는지
- public 경로는 인증 없이 통과하는지, 보호 경로는 401을 응답하는지
- 인증 성공 시 downstream이 받는 `X-Internal-Auth` audience가 라우트 대상과 일치하는지
- 클라이언트가 위조 주입한 `X-Internal-Auth`가 항상 제거되는지

## AI 작업 메모

- Gateway는 인증(Access Token 검증 + Internal Auth Token 발급)을 수행한다. 인가(role/권한 판단)나 도메인 규칙은 backend가 수행한다.
- Queue와 Ticket의 path 우선순위가 중요하다. `/api/v1/queue/**` route가 일반 `/api/**`보다 먼저 매칭되어야 한다.
- public 경로 화이트리스트는 `GatewayAuthFilter`에 하드코딩되어 있다. backend의 endpoint가 추가/변경되면 이 화이트리스트도 함께 확인한다.
- CORS 변경은 frontend origin, credential, WebSocket 연결에 미치는 영향을 함께 확인한다.
- secret(`JWT_SECRET`, `INTERNAL_AUTH_SECRET_KEY`)은 운영 환경에서 반드시 override 한다. 개발용 기본값을 운영에 그대로 두면 토큰 위조가 가능하다.
