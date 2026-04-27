---
api_id: user.createUser
http_method: POST
path: /api/v1/users
auth: N
controller: UserApiController.kt
handler: createUser
status: mined
---

# POST /api/v1/users — 회원가입(LOCAL)

## 1. 요청 (Request)
- Body: `UserDto.RegisterRequest` (`@Valid`)
- 인증 미요구

## 2. 응답 (Response)
- 성공: `201 Created` + `UserDto.RegisterResponse(userInfo)`

## 3. 비즈니스 로직 (요약)
1. `userFacade.registerUser(command)` → 사용자 생성.

## 4. 데이터 의존
- DB write: users

## 5. 예외 케이스
- 이메일 중복 → 409
- validation → 400

## 6. 암묵적 로직 (Implicit)
- 이메일 검증(validation-number/validation-email)이 선행되어야 하는지 정책 확인.
- 응답에 토큰이 포함되는지(자동 로그인) 확인.

## 7. 호출자 (Clients)
- 모바일/웹 (가입 화면)

## 8. TODO / Open Questions
- [ ] 비밀번호 해싱 알고리즘
- [ ] 이메일 인증 선행 강제 여부

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27, [identity/user-profile-domain-boundary](../../decisions/identity/user-profile-domain-boundary.md) 와 함께 결정

PRD 의 핵심 흐름(LOCAL 회원가입 → users 저장 → RegisterResponse 반환) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §8 의 "비밀번호 해싱 알고리즘" → **BCrypt (cost factor=12)**. Domain 에 `PasswordEncoder` port 신설, Infrastructure 에 Spring Security `BCryptPasswordEncoder` 어댑터 | 업계 표준, ADR-0001 인증 정책과 정합 |
| §8 의 "이메일 인증 선행 강제 여부" → **강제**. `validateEmail` 성공 시 Redis flag `user:email_validated:{email}` (TTL 10분) 설정 → `createUser` 진입 시 flag 확인 후 소비(delete). flag 없으면 400 `EMAIL_NOT_VALIDATED` | spam·오타 가입 방지 + 인증 흐름 완결성 |
| §6 의 "응답에 토큰이 포함되는지(자동 로그인)" → **자동 로그인 미적용**. `RegisterResponse { userId, email, displayName, createdAt }` 반환만, 토큰 발급 X. 가입 후 클라이언트가 별도 LOCAL 로그인 호출 (별도 PRD 후속) | 가입과 로그인의 책임 분리, refresh token rotation 흐름과의 일관성 |
| §1 / §2 응답 스키마 명시 — `RegisterResponse { userId: UUID, email: String, displayName: String, createdAt: Instant }` | PRD 본문에서 모호했던 응답 필드 확정 |
| §5 의 "이메일 중복 → 409" → **409 `EMAIL_ALREADY_REGISTERED`**. 단, `users.email` UNIQUE 제약은 두지 않음 ([ADR-0006](../../adr/0006-oauth-provider-integration.md)). LOCAL 가입 한정으로 어플리케이션 레이어에서 `existsByProviderAndEmail(LOCAL, email)` 로 검증 | ADR-0006 의 email auto-link 금지 정책과 정합 |

**구현 PR**: 추후 PR (`PasswordEncoder` port + email pre-validation flag 소비 + LOCAL 가입 application service + presentation)
