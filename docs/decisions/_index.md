# Decision Log Index

ADR 보다 **가벼운 단위의 결정 기록**. 한 도메인/모듈 안에서만 영향 받는 정책이나, 코드만 봐서는 의도가 안 드러나는 작은 트레이드오프를 적는다.

## ADR 와의 차이

| 항목 | ADR | Decision Log |
|---|---|---|
| 영향 범위 | 여러 서비스/모듈 | 단일 도메인/모듈 |
| 변경 비용 | 큼 (수많은 코드 영향) | 작음 (해당 도메인 안에서) |
| 위치 | `docs/adr/NNNN-*.md` | `docs/decisions/{domain}/{slug}.md` |
| 인덱스 | `docs/adr/_index.md` | 본 파일 |

## 작성 가이드

- 파일명: `docs/decisions/{domain}/{kebab-slug}.md`
- 권장 구조: `상태 / 컨텍스트 / 결정 / 근거 / 결과` (ADR 와 비슷하되 옵션 비교는 선택)
- 머지 후 본 인덱스에 한 줄 추가

## 인덱스

| 도메인 | 제목 | 상태 | 날짜 | 한 줄 요약 |
|---|---|---|---|---|
| auth | [Refresh Token Hash 알고리즘](auth/refresh-token-hash.md) | Accepted | 2026-04-27 | HMAC-SHA256 + pepper (envvar) — bcrypt 는 과잉, SHA-256 단독은 rainbow 표 위험 |
| auth | [ConfigurationProperties 패키지 위치](auth/properties-package-location.md) | Accepted (단기 우회) | 2026-04-27 | Application → Infrastructure config 의존을 단기 허용. PR4 에서 패키지 이동 또는 Settings 추상화 |
| auth | [OAuth Cookie 정책](auth/cookie-policy.md) | Accepted | 2026-04-27 | State cookie (Path=/api/v1/auth/oauth, 5분) + Device cookie (Path=/, 1년) — HttpOnly+Lax+Secure(prod) |
| auth | [Refresh 회전 + Blacklist 도메인 Port](auth/refresh-rotation-blacklist-ports.md) | Accepted | 2026-04-27 | SessionBlacklist / SessionIdGenerator port + RefreshTokenStore.findAllSessionIds — Refresh 예외 3종 분리, SessionId VO 격상은 보류 |
| auth | [Presentation 응답 ErrorCode + 인증 메커니즘](auth/presentation-error-policy.md) | Accepted | 2026-04-27 | REUSE→INVALID 통합 + 인증 실패 단일 UNAUTHORIZED + @LoginUser ArgumentResolver (Spring Security 보류) — 별도 PR 9건 후속 명시 |
