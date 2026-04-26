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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: rate limit 추가 → `@FIX`
