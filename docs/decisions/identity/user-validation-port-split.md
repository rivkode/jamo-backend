# Decision: User 도메인 이메일 검증 Port 3-분리

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun (ddd-architect 검토 반영)
- **PR**: PR5-a (`feature/identity-user-email-validation-domain`)
- **관련 PRD**: [`prd/user/sendValidationNumber.md`](../../prd/user/sendValidationNumber.md), [`prd/user/validateEmail.md`](../../prd/user/validateEmail.md)
- **관련 결정**: [`decisions/auth/refresh-rotation-blacklist-ports.md`](../auth/refresh-rotation-blacklist-ports.md) (같은 책임별 분리 패턴 선례)

## 컨텍스트

PR #19 의 PRD §9 가 `EmailSender` + `ValidationCodeStore` 두 port 를 가정. 그러나 `ValidationCodeStore` 가 다음 4가지 책임을 모두 떠안으면 SRP 위반·테스트 격리 저하 우려.

| 책임 | Redis key | TTL |
|---|---|---|
| (a) 코드 발급 / 조회 / 무효화 | `user:validation:code:{email}` | 5분 |
| (b) 검증 시도 카운터 | `user:validation:attempts:{email}` | 5분 (코드와 동기화) |
| (c) rate limit (30s 쿨다운 + 1일 10회) | `user:validation:rate:{email}` | 1일 |
| (d) email_validated flag (createUser 사전조건) | `user:email_validated:{email}` | 10분 |

## 검토한 옵션

| 옵션 | 책임 분포 | 평가 |
|---|---|---|
| A. 단일 큰 port | 1 port × 7-9 메서드 | SRP 위반, 테스트 mocking 부담 ↑ |
| **B. 3-port (라이프사이클별)** | code+attempts (a+b), rate(c), flag(d) | 본 결정 |
| C. 4-port (책임당) | a / b / c / d 각각 | (a)+(b) 원자성 깨질 위험 — 5회 잠금 시 코드+카운터 동시 무효화 필요 |

## 결정 — **옵션 B**

3개 port (+ EmailSender 1개 포함하면 총 4개):

- **`ValidationCodeStore`** — (a) + (b). 코드와 시도 카운터는 동일 라이프사이클(5분), 잠금 시 동시 invalidate.
- **`ValidationRateLimiter`** — (c). 1일 카운터 + 30초 쿨다운. 코드 라이프사이클과 무관.
- **`EmailValidatedFlag`** — (d). 검증 성공 후 createUser 사전조건. 별도 10분 TTL, 소비형.
- (이메일 외부 발송 자체는 별도 `EmailSender` port — 본 결정과는 독립.)

## 근거

1. **auth 도메인 선례 일관** — `RefreshTokenStore` / `SessionBlacklist` / `OAuthFlowSessionStore` / `AuthorizationCodeStore` 모두 책임별 분리. 새 도메인이 동일 패턴 따라가면 학습 비용 ↓.
2. **(a)+(b) 묶음의 응집** — attempts 카운터는 코드 인스턴스의 부속 상태. 코드 만료 시 attempts 도 같이 삭제, 5회 잠금 시 동시 invalidate. 분리하면 원자성 깨질 위험 (옵션 C 거부 사유).
3. **(c) 분리** — rate limit 은 코드 인스턴스가 없을 때도 동작 (발급 전 호출). TTL 1일 ≠ 5분.
4. **(d) 분리** — createUser 가 의존하는 사전조건. user 도메인 내 다른 use case (password reset 등 PRD 에서 언급) 와도 재사용 가능.
5. **port 분산의 결합도 우려는 작음** — application service 단에서만 port 들을 조립. send 흐름은 2-3 port 호출, verify 흐름은 3 port 호출 — 충분히 작음.

## 책임 매트릭스

| Port | 메서드 | Redis key | TTL | 호출자 |
|---|---|---|---|---|
| `ValidationCodeStore` | issue / find / incrementAttempts / invalidate | code: `user:validation:code:{email}` / attempts: `user:validation:attempts:{email}` | 5분 | send (issue), verify (find / incrementAttempts / invalidate) |
| `ValidationRateLimiter` | canSend / recordSend | `user:validation:rate:{email}` | 1일 | send |
| `EmailValidatedFlag` | mark / consume | `user:email_validated:{email}` | 10분 | verify (mark), createUser (consume — PR6) |
| `EmailSender` | sendValidationCode | (외부) | — | send |

## 결과 및 영향

### 본 슬라이스 (PR5-a)
- 4개 port 인터페이스 + `ValidationCode` VO + `EmailValidationException` base + 4 sub 예외.

### 후속 (PR5-b)
- Redis 어댑터 3개 (`ValidationCodeRedisStore`, `ValidationRateLimiterRedisStore`, `EmailValidatedFlagRedisStore`).
- SMTP/SES 어댑터 결정은 별도 결정 문서 (`email-sender-impl.md`) 후속.
- Application service 2개 (send / verify) 가 port 들을 조립.

### Non-Goals
- `incrementAttempts` 가 lock 상태 자체를 표현하는지 — 본 결정에선 단순 counter 반환, lock 판정은 application 책임.
- 4개 port 가 향후 너무 잘게 쪼개졌다 판단되면 (a)+(b)+(c) 통합 옵션 재고 — 단 PR5-b 구현 후 판단.

## 참고

- [PRD `sendValidationNumber.md` §9](../../prd/user/sendValidationNumber.md)
- [PRD `validateEmail.md` §9](../../prd/user/validateEmail.md)
- [`decisions/auth/refresh-rotation-blacklist-ports.md`](../auth/refresh-rotation-blacklist-ports.md) — 같은 책임별 분리 패턴 선례
