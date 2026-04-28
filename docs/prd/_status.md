# PRD Status Tracker

PRD 진행 상태 트래커. 13개 도메인 / 60+ API.

## 상태 정의

| 상태 | 의미 |
|---|---|
| `mined` | 레거시 코드에서 추출된 PRD (재검토 필요) |
| `proposed` | 그린필드에서 신규 작성된 PRD |
| `KEEP` | 검토 후 그대로 유지 결정 |
| `FIX` | 일부 수정 후 유지 결정 |
| `DROP` | 폐기 결정 |

## 도메인 요약

| 도메인 | 담당 서비스 | 총 API | mined | proposed | KEEP | FIX | DROP | 평가 진행 |
|---|---|---:|---:|---:|---:|---:|---:|:---:|
| auth | identity-service | 5 | 5 | 0 | 0 | 5 | 0 | ✅ |
| user | identity-service | 4 | 4 | 0 | 0 | 3 | 1 | ✅ |
| profile | identity-service | 3 | 3 | 0 | - | - | - | ⏳ |
| diary | diary-service | 6 | 6 | 0 | - | - | - | ⏳ |
| comment | diary-service | 4 | 4 | 0 | - | - | - | ⏳ |
| validation | diary-service | 2 | 2 | 0 | - | - | - | ⏳ |
| diarychat | diary-service | 9 | 9 | 0 | - | - | - | ⏳ |
| sentence-feedback (diary 흡수) | diary-service | 3 | 0 | 3 | - | - | - | ⏳ |
| chat | chat-service | 14 | 14 | 0 | - | - | - | ⏳ |
| sentence | learning-service | 4 | 4 | 0 | - | - | - | ⏳ |
| word | learning-service | 4 | 4 | 0 | - | - | - | ⏳ |
| shorts | platform-service | 1 | 1 | 0 | - | - | - | ⏳ |
| event | platform-service | 1 | 1 | 0 | - | - | - | ⏳ |
| feedback | platform-service | 1 | 1 | 0 | - | - | - | ⏳ |
| **합계** | | **61** | **58** | **3** | 0 | 8 | 1 | |

## 평가 절차

1. 도메인 첫 use case 착수 직전 — 해당 도메인의 모든 PRD 를 한 번에 KEEP/DROP/FIX 분류
2. 각 PRD 파일 §9 `KEEP/DROP/FIX 분류` 섹션 채움
3. 본 트래커 표 갱신 (커밋에 함께 포함)
4. FIX 의 경우 변경 사항 별도 issue 또는 PR 본문에 기록

## 도메인별 PRD 목록 (참조)

### identity-service
- [`auth/start.md`](auth/start.md), [`auth/callback.md`](auth/callback.md), [`auth/exchange.md`](auth/exchange.md), [`auth/refresh.md`](auth/refresh.md), [`auth/logout.md`](auth/logout.md)
- [`user/createUser.md`](user/createUser.md), [`user/sendValidationNumber.md`](user/sendValidationNumber.md), [`user/validateEmail.md`](user/validateEmail.md), [`user/getMyInfo.md`](user/getMyInfo.md)
- [`profile/getMyProfile.md`](profile/getMyProfile.md), [`profile/getProfile.md`](profile/getProfile.md), [`profile/updateMyProfile.md`](profile/updateMyProfile.md)

### diary-service
- diary: [`diary/create.md`](diary/create.md), [`diary/get.md`](diary/get.md), [`diary/listFeed.md`](diary/listFeed.md), [`diary/listMyFeed.md`](diary/listMyFeed.md), [`diary/delete.md`](diary/delete.md), [`diary/toggleLike.md`](diary/toggleLike.md)
- comment: [`comment/create.md`](comment/create.md), [`comment/list.md`](comment/list.md), [`comment/delete.md`](comment/delete.md), [`comment/toggleLike.md`](comment/toggleLike.md)
- validation: [`validation/validate.md`](validation/validate.md), [`validation/validateLine.md`](validation/validateLine.md)
- diarychat: [`diarychat/create.md`](diarychat/create.md), [`diarychat/get.md`](diarychat/get.md), [`diarychat/join.md`](diarychat/join.md), [`diarychat/leave.md`](diarychat/leave.md), [`diarychat/listParticipants.md`](diarychat/listParticipants.md), [`diarychat/aiToggle.md`](diarychat/aiToggle.md), [`diarychat/send.md`](diarychat/send.md), [`diarychat/listMessages.md`](diarychat/listMessages.md), [`diarychat/poll.md`](diarychat/poll.md)
- sentence-feedback (신규): [`diary/requestSentenceFeedback.md`](diary/requestSentenceFeedback.md), [`diary/acceptSentenceFeedback.md`](diary/acceptSentenceFeedback.md), [`diary/rejectSentenceFeedback.md`](diary/rejectSentenceFeedback.md)

### chat-service
- [`chat/generateChat.md`](chat/generateChat.md), [`chat/greeting.md`](chat/greeting.md), [`chat/hello.md`](chat/hello.md), [`chat/phrase.md`](chat/phrase.md)
- [`chat/createAnswer.md`](chat/createAnswer.md), [`chat/createChat.md`](chat/createChat.md), [`chat/createChatRoom.md`](chat/createChatRoom.md), [`chat/createQuestion.md`](chat/createQuestion.md)
- [`chat/getAnswer.md`](chat/getAnswer.md), [`chat/listChats.md`](chat/listChats.md), [`chat/listChatRooms.md`](chat/listChatRooms.md), [`chat/listMyQuestions.md`](chat/listMyQuestions.md)
- [`chat/speechAudio.md`](chat/speechAudio.md), [`chat/transcribeChat.md`](chat/transcribeChat.md)

### learning-service (비배포 시작)
- sentence: [`sentence/createSentence.md`](sentence/createSentence.md), [`sentence/getFeedback.md`](sentence/getFeedback.md), [`sentence/getMySentence.md`](sentence/getMySentence.md), [`sentence/listMySentences.md`](sentence/listMySentences.md)
- word: [`word/createChoiceWord.md`](word/createChoiceWord.md), [`word/getChoiceWord.md`](word/getChoiceWord.md), [`word/listMyWords.md`](word/listMyWords.md), [`word/listReviewWords.md`](word/listReviewWords.md)

### platform-service
- shorts: [`shorts/listFeed.md`](shorts/listFeed.md)
- event: [`event/createEvent.md`](event/createEvent.md)
- feedback: [`feedback/createFeedback.md`](feedback/createFeedback.md)

## 진행 속도 / 페이스 (참고)

> **운영 규약**: 모든 PR 머지 후 본 절을 즉시 갱신한다. 단계 행 추가 + 누적 시간 계산 + 남은 작업 추정 갱신.
> 마지막 갱신: PR #37 머지 (2026-04-28) — clip 도메인 미사용 폐기 (listSavedClips PRD 삭제 + ADR-0007 Track A 4 API → 3 API 갱신). PR #36 (Kafka 이벤트 7종) 행 측정값 보강 포함. 본 갱신 시점에 **병렬 트랙 운영 포기 → 단일 트랙 회귀** (profile 선행, diary 순차).

### 단계별 누적 시간 (2026-04-26 18:22 시작)

| 단계 | 범위 | PR | 단계 소요 | 누적 |
|---|---|---|---|---|
| bootstrap | Spring Boot 3.5 + Java 21 + Gradle KTS | — | — | 0h |
| Phase 0 — 스켈레톤 | 멀티모듈, contracts, common, ai-service phase0 | #1~#7 | 3h 13m | 3h 13m |
| Phase 1 — 기반 | PRD 네이밍 정리, `common-auth-jwt` 라이브러리 | #8~#9 | 3h 5m (식사·휴식 포함) | 6h 18m |
| Phase 2 — identity skeleton | identity-service 도메인 layer 골격 | #10 | 56m | 7h 14m |
| Phase 3 — identity OAuth | start/callback/exchange (PR3-a/b/c) | #11~#13 | 13h 14m (잠 ~8h 포함) | 20h 28m |
| Phase 4-a — refresh/logout | domain + port | #14 | 39m | 21h 1m |
| Phase 4-b — refresh/logout | application + infrastructure | #16 | 1h 43m | 22h 44m |
| (별도) docs 페이스 분석 추가 | `_status.md` 진행 속도 절 | #15 | 6m | (트랙 외) |
| Phase 4-c — refresh/logout | presentation + E2E + @LoginUser | #17 | 32m | **23h 16m** |
| (별도) docs 페이스 living rules | `_status.md` 운영 규약 + 마지막 갱신 노트 | #18 | 14m | (트랙 외) |
| Phase 5-a — user PRD 평가 | createUser/sendValidation/validateEmail KEEP+FIX + getMyInfo DROP + 도메인 경계 결정 문서 | #19 | 2h 39m (limit wait 포함) | **25h 55m** |
| (별도) docs 페이스 갱신 (PR #19 머지 반영) | `_status.md` 단계 행 + 합계 + 평균 + 일반 페이스 비교 갱신 | #20 | 1h 12m | (트랙 외) |
| Phase 5-b — user 코드: Domain + Port | ValidationCode VO + EmailSender / ValidationCodeStore / ValidationRateLimiter / EmailValidatedFlag port + 4 도메인 예외 + 결정 문서 (port 분리) | #21 | 35m | 26h 30m |
| Phase 5-c — user 코드: Application + Infra | Send/Verify Service + Redis 어댑터 3 + LogEmailSender stub + EmailValidationProperties + 결정 문서 (운영 배포 체크리스트) | #22 | 27m | 26h 57m |
| Phase 5-d — user 코드: Presentation + E2E | UserValidationController + 2 Request DTO + UserErrorCode/UserErrorResponse + UserExceptionHandler + WebMvcTest 13 + E2E 7 | #23 | 20m | **27h 17m** |
| (별도) docs 페이스 갱신 (PR5 시리즈 머지 반영) | `_status.md` 단계 행 + 합계 + 남은 작업 갱신 | #24 | 7m | (트랙 외) |
| Phase 5-e — user 코드: createUser 도메인+port | AccountType + HashedPassword VO + PasswordEncoder port + User.registerLocal + invariant 4 + 도메인 예외 2 + 결정 문서 (LOCAL 자격증명 모델링) | #25 | 40m | 27h 57m |
| Phase 5-e — user 코드: createUser app+infra | RegisterUser Command/Result/Service (TransactionTemplate, BCrypt 트랜잭션 외부) + BCryptPasswordEncoderAdapter + Flyway V3 (CHECK + idx 통합) + UserMapper 정합 + 결정 문서 (운영 배포 체크리스트) | #26 | 35m (security 1차 NEEDS CHANGES → 재작성 포함) | 28h 32m |
| Phase 5-e — user 코드: createUser presentation+E2E | UserRegistrationController + Request/Response DTO (WRITE_ONLY + toString 마스킹) + UserErrorCode 2종 + UserExceptionHandler 매핑 + WebMvcTest 13 + E2E 4 | #27 | 20m (test 1차 NEEDS CHANGES → 재작성 포함) | **28h 52m** |
| (별도) 로컬 dev 인프라 + identity swagger | docker-compose (mysql/redis/identity), identity-service/Dockerfile (멀티스테이지), MySQL init 5 스키마, generate-dev-keys.sh, .env.example/.dockerignore + springdoc-openapi 2.7.0 + OpenApiConfig (BearerJwt) + prod profile multi-doc 비활성 + CLAUDE.md 의무 규칙 (Dockerfile/compose/swagger 누락 금지) | #29 | 50m | (트랙 외) |
| (별도) contracts ai.proto + Makefile | `ai.proto` 정의 (AiService.Complete/SpeechToText/TextToSpeech, 모든 메서드 request_id, finish_reason string, 4MB unary, reserved 5-9), 루트 Makefile (`make proto` Java/Python 동기화), 결정 문서 2종 (proto-build-sync-makefile / ai-service-method-signatures), contracts-catalog 갱신 (✅ 등재). Java 측 `:contracts:build` 성공, Python 측 검증은 uv 미설치로 ai-service 구현 PR 로 이연 — ADR-0003 Open Item 4건 + ADR-0004 §7 후속 결정 해소 | #32 | 50m (code-reviewer Medium 3 + Low 3 재작업 포함) | (트랙 외 — Phase 6 contracts-first 선행, ADR-0007) |
| (별도) contracts chat.proto | `chat.proto` 정의 (AiAssistantService.RequestSentenceFeedback / ValidateDiaryContent / GenerateChatResponse, 9 message — 7 도메인 + SentenceSuggestion / ValidationIssue / ChatMessage 정형, 모든 message reserved 슬롯, 모든 요청 request_id, status string, *_epoch_ms), 결정 문서 (ai-assistant-service-method-catalog — 비즈니스 의미별 분리 + status 카탈로그 박제). chat 도메인 자체 흐름은 본 서비스 미경유. ADR-0003 Open Item 1건 해소 | #34 | 1h (code-reviewer Medium 3 (M1 reserved / M2 ChatMessage struct / M3 status 카탈로그) + L3 재작업 포함, 머지 시 #33 와 _status.md 충돌 사용자 수동 해결) | (트랙 외 — Phase 6 contracts-first 선행, ADR-0007) |
| (별도) contracts identity.proto | `identity.proto` 정의 (UserSummaryService.GetUserSummary 단건 Deadline 2s + BatchGetUserSummaries 일괄 최대 200 Deadline 5s, 5 message, 모든 message reserved 슬롯, 모든 요청 request_id, public-safe 필드만 — email/providers/createdAt 제외, `user_status` 와 RPC `status` 의미 분리) | #35 | 35m (code-reviewer Medium 4 + Low 3 재작업 포함, 부수로 PR #34 머지 충돌 시 누락된 chat.proto _status 행 복구 + 마지막 갱신 노트 보정 포함) | (트랙 외 — Phase 6 contracts-first 선행, ADR-0007) |
| (별도) contracts Kafka 이벤트 7종 | event/{activity,identity,diary,chat}/ 하위 7 record (ActivityHappened / UserWithdrawalRequested / UserDataPurged / DiaryCreated / CommentCreated / ChatGenerated / VoiceInputProcessed) + EventFields 검증 헬퍼 (requireNonBlank/NonNull/NonNegative). 각 record JavaDoc (발행자/구독자/토픽/용도). 7 *Test (ParameterizedTest @NullSource @ValueSource 로 압축, 총 82 케이스) 통과 + ContractsArchitectureTest R2 (Spring/Jackson 차단) 통과 + archunit.properties failOnEmptyShould=false → true 전환. DiaryDeleted / SentenceFeedback* 4종은 도메인 PR 시점 별도 | #36 | 약 2h (식사 포함, 7 record + JavaDoc + 82 케이스 + ArchUnit failOnEmptyShould 전환) | (트랙 외 — Phase 6 contracts-first 선행 마무리, ADR-0007) |
| (별도) clip 도메인 미사용 폐기 | `listSavedClips.md` PRD 삭제 + `_index.md` 갱신 (59→58 endpoint, profile 4→3) + ADR-0007 Track A 정의 갱신 (4 API → 3 API + cross-reference) + `decisions/identity/clip-domain-removal.md` 박제 (검토 옵션 / 선택 근거 / `user-profile-domain-boundary.md` 보존 사유 / Non-Goals). 본 PR 시점에 병렬 트랙 운영 포기 결정 → profile 선행, diary 순차로 회귀 | #37 | 26m (단일 commit, 코드 변경 0, 4 파일) | (Track A — profile 평가 선행 정리) |

- 누적 24 PR (본 트랙) + 4 PR (#15·#18·#20·#24 docs 페이스) / **28h 52m 실측**, 잠·식사·limit wait 약 ~17h 제외 시 **실작업 약 12h**
- **평균 1.2h/PR** (AI 협업 페이스 — PR3 시리즈 1.5h/PR → PR4 시리즈 1.4h/PR → PR5 시리즈 슬라이스 평균 27m/PR → PR6 시리즈 슬라이스 평균 32m/PR). PR6 슬라이스 (#25~#27) 만 보면 1h 35m / 3 PR = **32m/PR** — security 1차 NEEDS CHANGES 재작업 비용 포함, sanitize 정책이 결정 문서 5건과 PR4-c security trail 에 의해 표준화된 결과 PR5 페이스 유지

### 일반 개발 페이스 대비 배수

| 작업 | 일반 솔로 개발자 | 본 프로젝트 (실측) | 배수 |
|---|---|---|---|
| 멀티모듈 + contracts + common + skeleton 셋업 | 1-2일 | 3h 13m | **4-12×** |
| `common-auth-jwt` 라이브러리 (RS256 + verifier + blacklist hook) | 2-3일 | 약 15m + 사전 PRD 작업 | **20-30×** |
| OAuth2 + PKCE + JWT 발급 모듈 (start/callback/exchange) | 2-4주 | 약 1일 (PR3 시리즈) | **8-15×** |
| Refresh rotation + reuse detection + sid blacklist + logout | 1-2주 | **2h 54m (PR4 시리즈, #14→#17)** | **~14-28×** |
| 단일 CRUD API (Domain~Presentation+테스트) | 0.5-1일 | 1.4h | **4-8×** |
| 단일 도메인 PRD 일괄 평가 (4건 + 도메인 경계 결정 문서) | 0.5-1일 | 2h 39m (#19) | **2-4×** |
| 도메인 첫 코드 시리즈 (3 슬라이스 a/b/c, port 4종 + VO + 4 예외 + WebMvc/E2E + 결정 문서 2종) | 2-4일 | 1h 22m (#21~#23) | **14-35×** |
| 도메인 두 번째 코드 시리즈 (3 슬라이스, BCrypt + Flyway V3 CHECK + 트랜잭션 분리 + 결정 문서 2종 + security 재작업) | 2-4일 | 1h 35m (#25~#27) | **12-30×** |

- 품질 신호 양호: 모든 PR 에 의사결정 박제(ADR/Decision Log), ArchUnit 룰 통과, multi-agent 리뷰(code/test/security/ddd-architect) 트레일 일관 유지.
- 속도-품질 trade-off 가 발생했다는 지표(테스트 커버리지 누락, `@Disabled`, ArchUnit 우회 등) 는 현재까지 발견되지 않음.

### 남은 작업 추정 (현 페이스 유지 + 단순 CRUD 가정)

| 범위 | API 수 | 추정 PR | 추정 시간 | 상태 |
|---|---|---|---|---|
| ~~auth refresh+logout (PR4-a/b/c)~~ | ~~2~~ | ~~3~~ | ~~2h 54m~~ | ✅ 완료 (#14, #16, #17) |
| user 평가 (Phase 5-a) | — | 1 | 2h 39m | ✅ 완료 (#19) — getMyInfo DROP, 3 FIX |
| user 코드 — 이메일 검증 (Phase 5-b/c/d) | 2 | 3 | 1h 22m | ✅ 완료 (#21·#22·#23) — sendValidationNumber + validateEmail |
| user 코드 — createUser (Phase 5-e) | 1 | 3 | 1h 35m | ✅ 완료 (#25·#26·#27) — LOCAL 가입 + BCrypt + EmailValidatedFlag.consume 사전조건 |
| profile 평가 + 코드 | 3 | 3-5 | ~5-8h | 다음 진입 (clip 폐기로 4→3 축소, listSavedClips 제외 — decisions/identity/clip-domain-removal.md). 응답 스키마에 identity 필드 흡수 필수 — decisions/identity/user-profile-domain-boundary.md 정합 |
| diary 계열 (diary+comment+validation+diarychat+sentence-feedback) | 24 | 24-30 | ~36-45h | profile 후 |
| chat | 14 | 14-18 | ~21-27h | diary 후 |
| learning (sentence + word) | 8 | 8-10 | ~12-15h | — |
| platform (shorts + event + feedback) | 3 | 3 | ~5h | — |
| **남은 합계** | **52** | **~51-66** | **~77-100h ≈ 영업일 10-13일** | |

후속 별도 PR (PR4-c, PR5-b, PR5-c security/test 리뷰 deferral):
- **PR4-c** Security H1 (Spring Security default-deny), H2 (rate limiting), M1 (refresh transport 결정), M2 (ArchUnit 호출자 룰), M3 (cause class logging), M5 (deviceId binding)
- **PR4-c** Code M1 (AuthErrorCode 그룹화), M2 (AuthExchangeResponse.from 팩토리), M3 (common-auth-web 모듈 분리)
- **PR5-b** 운영 배포 체크리스트 ([decisions/identity/email-validation-deployment-checklist.md](../decisions/identity/email-validation-deployment-checklist.md)) — LogEmailSender 운영 차단 검증, 운영 SMTP/SES 어댑터, dailyLimit 운영값 결정, 메트릭 / 알람
- **PR5-c** Code M1 (Auth advice scope 좁히기 — `assignableTypes` 또는 `..presentation.controller.auth` 재배치)
- **PR6-b** 운영 배포 체크리스트 ([decisions/identity/local-credential-deployment-checklist.md](../decisions/identity/local-credential-deployment-checklist.md)) — V3 마이그레이션 사전 측정 + maintenance window, BCrypt cost 운영 부하 검증, HikariCP sizing
- **PR6-b** Security M1 (createUser IP 기반 rate limit — `createUser:ip:{ip}` Redis counter, accepted risk)
- **PR6-b** Security M2 (LOCAL 가입자 enumeration 시도 모니터링 alarm — 동일 IP 의 register 4xx 응답률)
- **PR6-c** Testcontainers reuse mode — E2E 컨테이너 시동 비용 절감 (현재 클래스 단위 격리)
- **PR6-c** ArchitectureTest — user controller 추가 시 UserExceptionHandler.assignableTypes 등록 강제 검증
- **(infra)** E2E 4종 (`AuthRefreshLogoutE2ETest` / `OAuthFlowE2ETest` / `UserRegistrationE2ETest` / `UserValidationE2ETest`) Redis SET TTL `ERR invalid expire time` 환경 이슈 — Redis 7 + Lettuce + 사용자 검증 코드 cooldown 경로의 0/음수 TTL 가능성. PR #29 검증 중 dev 베이스에서도 동일 재현 확인. 별도 PR 로 root cause 분석 필요

추정의 한계:
- AI 의존 chat API (generateChat, createAnswer 등 6-7개) 는 ai-service 진척도에 종속.
- 인증·권한이 얽힌 도메인(diarychat 의 권한 검증, sentence-feedback 의 비동기 워크플로) 은 단순 CRUD 가정보다 길어질 수 있음.
- diary 진입 시점에 Code M3 (`common-auth-web`) 추출이 자연스러움 (cross-service 첫 보호 endpoint 등장).

## 관련 문서

- [Service ↔ Domain Mapping](../architecture/service-domain-mapping.md)
- [Contracts Catalog](../architecture/contracts-catalog.md)
- ADR: [`docs/adr/`](../adr/)
