---
api_id: user.sendValidationNumber
http_method: POST
path: /api/v1/users/validation-number
auth: N
controller: UserApiController.kt
handler: sendValidationNumber
status: mined
---

# POST /api/v1/users/validation-number — 이메일 검증번호 전송

## 1. 요청 (Request)
- Body: `UserDto.ValidateNumberRequest { email }` (`@Valid`)

## 2. 응답 (Response)
- 성공: `204 No Content`

## 3. 비즈니스 로직 (요약)
1. `userFacade.sendValidationNumberToEmail(request.email)` → 이메일 발송.

## 4. 데이터 의존
- 외부 API: SMTP/이메일 서비스 (SES, SendGrid 등)
- Redis: 검증번호 저장 (TTL 추정)

## 5. 예외 케이스
- 이메일 형식 오류 → 400

## 6. 암묵적 로직 (Implicit)
- **rate limit 없으면 spam 위험** — 동일 이메일에 짧은 주기로 무제한 호출 가능 여부 확인.
- 검증번호 TTL/길이 정책.

## 7. 호출자 (Clients)
- 가입 / 비밀번호 재설정

## 8. TODO / Open Questions
- [ ] rate limit 정책
- [ ] 검증번호 재전송 정책

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27

PRD 의 핵심 흐름(이메일 입력 → 코드 생성 → Redis 저장 → 이메일 발송) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §8 의 "rate limit 정책" → **per-email 30초당 1회 + 1일 10회**. Redis counter, key `user:validation:rate:{email}` (window 정책 — fixed vs sliding — 은 구현 PR 에서 결정) | spam 방지, OWASP brute-force 가이드 |
| §6 의 "검증번호 TTL/길이 정책" → **6자리 숫자, TTL 5분**. 재요청 시 기존 코드 무효화 후 새 코드 발급 | UX 표준 (네이버/카카오 회원가입 흐름) |
| §1 의 DTO 명 `ValidateNumberRequest` → **`SendValidationCodeRequest { email }`** 로 분리·개명 | 단일 책임 (validateEmail 의 verify DTO 와 분리, validateEmail.md FIX 와 짝) |
| 외부 의존 abstraction → **Domain 에 `EmailSender` port 신설**, Infrastructure 에 SMTP/SES 어댑터. SMTP vs SES 선택은 구현 PR 시 별도 결정 문서 (`docs/decisions/identity/email-sender-impl.md`) | DDD: 외부 시스템은 port-adapter 로 격리 |
| Redis 저장소 abstraction → **Domain 에 `ValidationCodeStore` port** (set/get/delete + counter). 기존 `RefreshTokenRedisStore` / `OAuthFlowSessionRedisStore` 패턴과 정합 | 일관 |

**구현 PR**: 추후 PR (`EmailSender` port + `ValidationCodeStore` port + Application service + presentation + Infrastructure 구현체). validateEmail 과 동일 PR 가능.
