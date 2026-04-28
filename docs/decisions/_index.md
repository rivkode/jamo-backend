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
| identity | [User vs Profile 도메인 경계 — `/me` 조회 단일화](identity/user-profile-domain-boundary.md) | Accepted | 2026-04-27 | `user.getMyInfo` DROP, `profile.getMyProfile` 가 identity 필드(id/email/displayName/providers/createdAt) 흡수. user 도메인은 write-only |
| identity | [User 도메인 이메일 검증 Port 3-분리](identity/user-validation-port-split.md) | Accepted | 2026-04-27 | `ValidationCodeStore` (code+attempts 5분) + `ValidationRateLimiter` (1일) + `EmailValidatedFlag` (10분 소비형) — 라이프사이클별 분리 |
| identity | [이메일 검증 흐름 운영 배포 체크리스트](identity/email-validation-deployment-checklist.md) | Accepted | 2026-04-27 | 운영 배포 BLOCK 조건 5건 (LogEmailSender 격리 / 메시지 sanitize / dailyLimit 5 / envvar override / 메트릭) |
| identity | [LOCAL 자격증명 모델링](identity/local-credential-modeling.md) | Accepted | 2026-04-27 | `users.account_type` + `password_hash` 컬럼 추가, `OAuthProvider.LOCAL` 미추가. AccountType invariant + PasswordEncoder port 도입 |
| identity | [LOCAL 회원가입 운영 배포 체크리스트](identity/local-credential-deployment-checklist.md) | Accepted | 2026-04-28 | V3 fail-safe (DEFAULT 제거 + CHECK 2종) / BCrypt 트랜잭션 외부 / enumeration accepted risk / yaml defensive error policy |
| contracts | [proto Java↔Python 빌드 동기화 — Makefile 채택](contracts/proto-build-sync-makefile.md) | Accepted | 2026-04-28 | ADR-0004 §7 후속 결정. `make proto` 가 Java (`./gradlew :contracts:generateProto`) + Python (`uv run python -m grpc_tools.protoc`) 양쪽 stub 생성. CI step (option d) 은 후속 ADR |
| contracts | [AiService gRPC 메서드 시그니처](contracts/ai-service-method-signatures.md) | Accepted | 2026-04-28 | ADR-0003 Open Item 4건 해소 — `Complete` / `SpeechToText` / `TextToSpeech` unary, 모든 메서드에 `request_id`, `finish_reason` string, 음성 4MB unary, server/client streaming 후속 |
| contracts | [AiAssistantService 메서드 카탈로그](contracts/ai-assistant-service-method-catalog.md) | Accepted | 2026-04-28 | 비즈니스 의미별 분리 채택 (`call(type, payload)` 일반화 거부). 초기 3 메서드 `RequestSentenceFeedback` / `ValidateDiaryContent` / `GenerateChatResponse`. chat 도메인 자체 흐름은 chat-service → ai-service 직접 호출 (자기 게이트웨이 미경유) |
| identity | [clip 도메인 미사용 폐기](identity/clip-domain-removal.md) | Accepted | 2026-04-28 | `listSavedClips` PRD 삭제 + `_index.md` (59→58 endpoint) + ADR-0007 Track A (4→3 API). clip 도메인이 본 백엔드 13 도메인에 부재. `user-profile-domain-boundary.md` 의 savedClips 언급은 history 보존 |
| identity | [profile 3 API 일괄 평가](identity/profile-prd-evaluation.md) | Accepted | 2026-04-28 | `getMyProfile` (private 8 필드, identity 5종 흡수) / `getProfile` (public-safe 4 필드, UserSummary 정합) / `updateMyProfile` (화이트리스트 4 필드 + displayName 7일 1회 + 고유성 미적용). follow / 비공개 차단 후속 |
| identity | [profile App+Infra 구현 결정](identity/profile-app-infra-decisions.md) | Accepted | 2026-04-28 | markChanged = `@TransactionalEventListener(AFTER_COMMIT)` (Y) / Profile 생성 시점 = Lazy + Read 기본값 (C) / 응답 합성 = 단일 트랜잭션 합성 / Flyway V4 (`profiles` 테이블, display_name 미생성, FK 미사용) |
| diary | [validation 도메인 — AI 검증 정책 / fallback / rate limit](diary/validation-ai-fallback-policy.md) | Accepted | 2026-04-28 | validate / validateLine 모두 `ValidateDiaryContent` (1 RPC) 재사용 / status `VALID|INVALID|FAILED` 카탈로그 / FAILED 우회 정책 / 룰 1차→LLM 2차 mode / 클라 debounce 500ms + quota 분당 10·60회 / 클라 타임아웃 15s·5s 분리 / userId propagation |
| diary | [comment 도메인 정책](diary/comment-domain-policy.md) | Accepted | 2026-04-28 | UUID 일관 / 답글 깊이 1단 제한 / hard-delete + cascade / 작성자 only (강제 삭제 X) / 404 통일 (IDOR) / cursor 페이징 (chronological, size max 100) / 좋아요 명시적 boolean 멱등 + likeCount 동시 반환 / CommentCreated Outbox 발행 / 알림은 후속 |
| diary | [diary 도메인 정책](diary/diary-domain-policy.md) | Accepted | 2026-04-28 | UUID / Visibility (PUBLIC·PRIVATE) + FOLLOWERS_ONLY 후속 / DiaryResponse 11 필드 (likeCount·commentCount·likedByMe viewer-context) / 검증 클라 사전 호출 (서버 강제 X) / category→tag 통일 / 페이징 (recent·popular, size max 100) / hard-delete + DiaryDeleted Saga cascade (2PC X, best-effort) / DiaryDeleted contracts 별도 PR / 조회수·저장 미지원 / 좋아요 boolean 멱등 |
| diary | [diarychat 도메인 정책](diary/diarychat-domain-policy.md) | Accepted | 2026-04-28 | UUID / 누구나 createOrGet 단일 방 (방장 미도입) / 참여 자격 = diary visibility 정합 (비공개 = 작성자 only 404) / 권한 가드 404 통일 / aiToggle 작성자 only / **`join` 의미 재해석 = 입장 + AI 응답 트리거** (첫 입장 welcome 프롬프트 / 재입장 일반 프롬프트, 매 호출 동기 LLM) / **leave DROP** / send text/audio 양립 + STT 동기 (`TranscribeUserAudio` 가칭 선행 필요) / **AI 응답 동기** (send 응답에 사용자+AI 메시지 둘 다 포함) / **TTS lazy GET** 신규 endpoint `getMessageAudio` (10번째) / poll WebSocket Non-Goals / chatroom **soft-delete** (`deleted_at`) + DiaryDeleted Saga / 4 endpoint `/welcome /tts /stt /chat` → 3 통합 |
