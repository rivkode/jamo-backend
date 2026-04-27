---
api_id: user.validateEmail
http_method: POST
path: /api/v1/users/validation-email
auth: N
controller: UserApiController.kt
handler: validateEmail
status: mined
---

# POST /api/v1/users/validation-email — 이메일 검증번호 확인

## 1. 요청 (Request)
- Body: `UserDto.ValidateNumberRequest` (⚠️ DTO명이 부정확 — number 발송과 검증 모두 동일 DTO 사용) (`@Valid`)

## 2. 응답 (Response)
- 성공: `204 No Content`
- 실패: 400 (검증 실패)

## 3. 비즈니스 로직 (요약)
1. `userFacade.validateNumber(request)` → 이메일 + 검증번호 매칭 확인.

## 4. 데이터 의존
- Redis: 검증번호 read/delete

## 5. 예외 케이스
- 검증번호 불일치/만료 → 400

## 6. 암묵적 로직 (Implicit)
- **DTO 재사용**: `ValidateNumberRequest`가 send와 verify 모두 사용 — 의도치 않은 필드 노출 가능.
- 시도 횟수 제한 정책 확인 필요.

## 7. 호출자 (Clients)
- 가입 / 비밀번호 재설정

## 8. TODO / Open Questions
- [ ] 검증 시도 횟수 제한
- [ ] DTO 분리

## 9. KEEP/DROP/FIX 분류

**판정: KEEP (with FIX)** — 2026-04-27

PRD 의 핵심 흐름(이메일 + 코드 입력 → Redis 매칭 → 성공/실패 응답) KEEP. 다음 항목 FIX:

| 변경점 | 근거 |
|---|---|
| §1 의 DTO 재사용 → **`VerifyValidationCodeRequest { email, code }`** 로 분리. send DTO (`SendValidationCodeRequest`) 와 분리 | §6 에서 PRD 자체가 지적한 사안. 단일 책임 + 의도치 않은 필드 노출 방지 |
| §6 의 "시도 횟수 제한" → **동일 코드 5회 실패 시 코드 무효화**. 재발급 강제 (사용자가 sendValidationNumber 다시 호출). counter Redis key `user:validation:attempts:{email}` | brute-force 방지 |
| 검증 성공 시 부수 효과 → **Redis flag `user:email_validated:{email}` (TTL 10분) 설정**. `createUser` 의 사전조건 flag (소비형). 응답은 204 그대로 (token 발급 X) | createUser 와의 통합 — flag 기반이 token 기반보다 단순. 서버 상태만으로 검증 가능 |
| §5 검증 실패 응답 → **400 분리**: `VALIDATION_CODE_MISMATCH` (불일치) / `VALIDATION_CODE_EXPIRED` (TTL 만료) / `VALIDATION_CODE_LOCKED` (5회 초과) | 클라이언트 UX 분기 (재입력 vs 재발급 vs 잠금 안내) |

**구현 PR**: 추후 PR (sendValidationNumber 와 동일 PR 가능).
