# Ticket Gateway AI 작업 규칙

이 파일은 AI 에이전트가 `ticket-gateway` 저장소에서 반드시 지킬 최소 규칙이다.

## 기본 원칙

- 모든 답변, 문서, 작업 로그는 한국어로 작성한다.
- 파일은 UTF-8, BOM 없이 유지한다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- 요청 범위 밖의 기능 추가, 대규모 리팩터링, 새 추상화는 하지 않는다.
- 파괴적 작업, 대량 삭제, `git reset`, `git checkout --`는 명시 요청 없이 수행하지 않는다.

## 먼저 읽을 순서

1. `README.md`
2. `settings.gradle`
3. `build.gradle`
4. `src/main/resources/application.yml`
5. `src/main/java/com/ticket/gateway/config` (특히 `GatewayAuthFilter.java`, `GatewayRoutesConfig.java`)
6. 관련 테스트 (`TicketGatewayApplicationTest.java`)

## 경계

- Gateway는 route, CORS, backend target 설정과 **인증(Access Token 검증 + Internal Auth Token 발급)** 을 담당한다.
- 인가(role/권한 판단)와 도메인 비즈니스 규칙은 Ticket Server와 Queue Server가 각각 수행한다.
- `Authorization`, `X-Queue-Session`, `X-Admission-Token`을 임의로 제거하거나 변형하지 않는다.
- 클라이언트가 보낸 `X-Internal-Auth`는 모든 경로에서 항상 제거하고, 인증 성공 시 Gateway가 audience를 분리해 새로 발급한다.
- `/api/v1/queue/**`는 일반 `/api/**`보다 먼저 Queue Server로 라우팅되어야 한다.

## 고위험 영역

- route 우선순위 변경은 Queue API가 Ticket API로 흘러가는 회귀를 만들 수 있다.
- 인증 필터(`GatewayAuthFilter`)의 public/protected 분기 변경은 보안 구멍(보호돼야 할 경로가 public 처리)이나 가용성 문제(public이어야 할 경로가 401)를 만든다.
- Internal Auth Token의 audience(`ticket-core`/`ticket-queue`) 변경은 downstream의 검증을 깨뜨릴 수 있으니 양쪽을 함께 확인한다.
- `JWT_SECRET`, `INTERNAL_AUTH_SECRET_KEY`는 32바이트 이상이어야 하며, 운영에서 개발용 기본값을 그대로 두면 토큰 위조가 가능하다.
- CORS 변경은 credential, frontend origin, WebSocket 연결에 영향을 준다.
- backend URI 기본값 변경은 로컬 개발 흐름과 배포 환경 변수를 함께 확인한다.
- WebSocket route는 REST route와 별도로 확인한다.

## 검증

작업 범위에 맞는 가장 좁은 명령부터 실행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

문서만 변경한 경우 Java 빌드 대신 아래를 우선한다.

```powershell
rg -n "확인할_문구" .
git diff --check
```

## 보고

마무리 보고에는 변경 파일, 핵심 변경점, 검증 결과, 남은 리스크를 포함한다.
