---
api_id: user.getMyInfo
http_method: GET
path: /api/v1/users/me
auth: Y
controller: UserApiController.kt
handler: getMyInfo
status: mined
---

# GET /api/v1/users/me — 내 사용자 정보

## 1. 요청 (Request)
- Header: `@LoginUser`

## 2. 응답 (Response)
- 성공: `200 OK` + `UserDto.InfoResponse`

## 3. 비즈니스 로직 (요약)
1. `userFacade.retrieveUserInfo(userId)` → 사용자 정보(이메일, provider, 가입일 등 추정).

## 4. 데이터 의존
- DB read: users

## 5. 예외 케이스
- 인증 실패 → 401

## 6. 암묵적 로직 (Implicit)
- profile.retrieveMyProfile과 응답 분리 — UserInfo와 ProfileResponse의 책임 구분 확인 필요(중복 정보).

## 7. 호출자 (Clients)
- 모바일/웹

## 8. TODO / Open Questions
- [ ] User vs Profile DTO/도메인 경계

## 9. KEEP/DROP/FIX 분류

**판정: DROP** — 2026-04-27, [identity/user-profile-domain-boundary](../../decisions/identity/user-profile-domain-boundary.md) 와 함께 결정

도메인 경계 정리 결정에 따라 본 endpoint 는 폐기한다. `/me` 조회는 `profile.getMyProfile` 단일 endpoint 로 통합하고, 응답에 identity 필드(id / email / displayName / providers / createdAt) 를 흡수한다.

| 사유 | 내용 |
|---|---|
| 클라이언트 호출 단순화 | 마이페이지 진입 시 `/users/me` + `/profiles/me` 2회 호출 → `/profiles/me` 1회로 RTT 절감 |
| 도메인 책임 분리 | user(가입·검증, write-only), profile(조회·수정, read+write) — `getMyInfo` 는 profile 의 read 책임과 중복 |
| §6 의 "profile 와의 책임 구분" Open Question | DROP 으로 해결 |
| §8 의 "User vs Profile DTO/도메인 경계" TODO | DROP 으로 해결 |

**후속 작업**: profile 도메인 평가 단계에서 `profile/getMyProfile.md` §1·§2 응답 스키마에 identity 필드 5종 추가 명시 필수 — 본 PR 범위 외, profile 평가 PR 에서 처리.

**구현 PR**: 없음 (DROP).
