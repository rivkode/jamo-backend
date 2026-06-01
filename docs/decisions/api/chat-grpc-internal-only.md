# AiAssistantService gRPC (chat-service:9093) — 내부 전용 + prod 배포 BLOCK 조건

- **상태**: Accepted (dev 한정) / prod 진입 전 BLOCK 조건 미충족
- **날짜**: 2026-06-02
- **영향 범위**: chat-service (gRPC server), diary-service (gRPC client)
- **관련**: S4 (diarychat AI 자동응답), contracts/chat.proto `AiAssistantService.GenerateChatResponse`, security-reviewer M1
- **선례**: [`diary-grpc-internal-only.md`](diary-grpc-internal-only.md) (9092, 동일 위협 모델)

## 컨텍스트

S4 에서 chat-service 가 처음으로 gRPC server (`AiAssistantService.GenerateChatResponse`) 를 노출하고,
diary-service 가 client 로 호출해 diarychat 방의 AI 자동응답을 생성한다 (ADR-0003: diary 는 ai-service 를
직접 호출하지 않고 chat-service 게이트웨이만 호출).

`ChatResponseRequest.user_id` 는 **호출자(diary-service)가 인증된 사용자 UUID 로 채워** 보낸다. chat-service 의
`InMemoryAiRateLimiter` 는 이 user_id 를 키로 사용량을 제한한다. 현 시점 gRPC server 에는 **인증 / authz
interceptor 가 없다** — chat-service 는 user_id 를 그대로 신뢰한다.

9092(DiaryQueryService)는 누설(비공개 일기 수 PII)이 위협이었으나, 9093 은 **직접 비용 발생**이 위협이다.
포트가 신뢰 경계 밖에 노출되면 임의 호출자가 임의 user_id(매 호출 랜덤화로 rate limit 우회) + 임의 user_message
로 **LLM 호출을 무제한 트리거**할 수 있다 (비용 abuse, security-reviewer M1).

## 결정

### 1. dev/local: host 포트 바인딩 금지

`docker-compose.yml` 에서 chat-service 의 gRPC 포트(9093)는 **host 에 바인딩하지 않는다** (`expose` 만).
같은 compose 네트워크의 diary-service 는 서비스명(`chat-service:9093`)으로 접근하므로 host 노출 불필요.
(`grpc.server.address` override 가 없어 컨테이너 내부에서는 `0.0.0.0:9093` 바인딩 — 외부 차단은 host 미바인딩 +
prod 의 네트워크 통제에 의존.)

### 2. prod 배포 BLOCK 조건 (전부 충족 전까지 prod 진입 금지)

- [ ] **(B1)** gRPC 포트가 외부망에 노출되지 않음 — k8s NetworkPolicy / 보안 그룹으로 diary-service ↔
      chat-service 만 허용.
- [ ] **(B2)** service-to-service 인증 — gRPC mTLS **또는** authz ServerInterceptor (호출자 신원 검증).
- [ ] **(B3)** authz 도입 시 `user_id` 를 **호출자가 전파한 검증된 subject 와 대조** — 위조 user_id 로 타인
      명의 AI 호출 / rate limit 우회 차단 (신뢰 경계 server 이전).
- [ ] **(B4)** AI 비용 분산 통제 — server-side Redis rate limit(INCR+TTL) + **전역 일일 budget circuit**
      (in-memory userId 가드는 다중 인스턴스/위조 시 우회됨, security-reviewer M2).

### 3. 위협 모델 (현 상태)

- **위협**: LLM 호출 비용 abuse (직접 금전 손실). 데이터 누설 아님.
- **1차 방어**: host 미바인딩(외부 차단) + 사용자별 in-memory rate limit(20/분, 기본) + diary-service
  접근 가드(ai-enabled 방 + ChatRoomAccessGuard 404 IDOR 통과한 send 에서만 트리거).
- **로그**: user_id / room_id / status / finishReason / exception class 만 — 사용자 메시지 / AI 응답 /
  프롬프트 본문 미로깅 (security-reviewer 확인).

## 근거

- dev 단계에서 mTLS / authz / Redis 분산 quota 는 과도 — S4 핵심(AI 자동응답)을 먼저 검증, 보안/비용 강화는
  prod 진입 전 별도 PR.
- host 포트 제거는 즉시 적용 가능한 1차 방어 (비용 0).
- in-memory rate limit 은 단일 인스턴스 1차 가드 — prod 비용 통제는 B4 로 해소.

## 결과

- dev compose 는 host 노출 없이 컨테이너간 통신만 — M1 1차 해소.
- prod 진입 PR 은 본 문서 §2 의 B1~B4 체크리스트를 충족해야 머지 — 미충족 시 prod 배포 BLOCK.
