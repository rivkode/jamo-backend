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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
- 후보: DTO 분리 → `@FIX`
