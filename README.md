# Ticket Gateway

기준일: 2026-05-24

Ticket 시스템의 단일 HTTP/WebSocket 진입점이다. Spring Cloud Gateway Server MVC 기반으로 Frontend 요청을 Ticket Server와 Queue Server로 라우팅한다.

## 빠른 맥락

- 애플리케이션: Spring Boot Gateway
- 기본 포트: `8000`
- Ticket Server 기본 대상: `http://localhost:8080`
- Queue Server 기본 대상: `http://localhost:8090`
- Frontend 기본 origin: `http://localhost:3000`
- 인증 판단은 Gateway가 아니라 각 backend가 수행한다.

## 라우팅

| External path | Internal target | 설명 |
| --- | --- | --- |
| `/api/v1/queue/**` | Queue Server | 대기열 진입, 상태 조회 |
| `/ws/**` | Ticket Server WebSocket | 좌석 상태 실시간 전파 |
| `/api/**` | Ticket Server REST API | 인증, 공연 조회, 좌석 선택, 주문 |

Gateway는 아래 header를 제거하지 않는다.

```text
Authorization
X-Queue-Session
X-Admission-Token
```

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

## 구조

```text
ticket-gateway
├── src/main/java/com/ticket/gateway
│   ├── TicketGatewayApplication.java
│   └── config
│       ├── GatewayRoutesConfig.java       # route 정의
│       ├── GatewayBackendProperties.java  # backend target 설정
│       ├── GatewayCorsConfig.java         # CORS 설정
│       └── GatewayCorsProperties.java
└── src/main/resources/application.yml
```

## 핵심 파일

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/ticket/gateway/TicketGatewayApplication.java` | 실행 진입점 |
| `src/main/java/com/ticket/gateway/config/GatewayRoutesConfig.java` | `/api/v1/queue/**`, `/ws/**`, `/api/**` route |
| `src/main/java/com/ticket/gateway/config/GatewayCorsConfig.java` | CORS 정책 |
| `src/main/resources/application.yml` | 포트, backend URI, actuator 설정 |

## 검증 명령

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

라우팅 변경 시 최소 확인:

- `/api/v1/queue/**`가 Queue Server로 가는지
- `/api/**`가 Ticket Server REST로 가는지
- `/ws/**`가 Ticket Server WebSocket으로 가는지
- 인증 관련 header가 backend까지 전달되는지

## AI 작업 메모

- Gateway에 인증/인가 비즈니스 판단을 넣지 않는다.
- Queue와 Ticket의 path 우선순위가 중요하다. `/api/v1/queue/**` route가 일반 `/api/**`보다 먼저 매칭되어야 한다.
- CORS 변경은 frontend origin, credential, WebSocket 연결에 미치는 영향을 함께 확인한다.
