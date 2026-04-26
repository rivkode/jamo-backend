---
api_id: user.registerUser
http_method: POST
path: /api/v1/users
auth: N
controller: UserApiController.kt
handler: registerUser
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

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
