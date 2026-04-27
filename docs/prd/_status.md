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
| user | identity-service | 4 | 4 | 0 | - | - | - | ⏳ |
| profile | identity-service | 4 | 4 | 0 | - | - | - | ⏳ |
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
| **합계** | | **62** | **59** | **3** | 0 | 5 | 0 | |

## 평가 절차

1. 도메인 첫 use case 착수 직전 — 해당 도메인의 모든 PRD 를 한 번에 KEEP/DROP/FIX 분류
2. 각 PRD 파일 §9 `KEEP/DROP/FIX 분류` 섹션 채움
3. 본 트래커 표 갱신 (커밋에 함께 포함)
4. FIX 의 경우 변경 사항 별도 issue 또는 PR 본문에 기록

## 도메인별 PRD 목록 (참조)

### identity-service
- [`auth/start.md`](auth/start.md), [`auth/callback.md`](auth/callback.md), [`auth/exchange.md`](auth/exchange.md), [`auth/refresh.md`](auth/refresh.md), [`auth/logout.md`](auth/logout.md)
- [`user/createUser.md`](user/createUser.md), [`user/sendValidationNumber.md`](user/sendValidationNumber.md), [`user/validateEmail.md`](user/validateEmail.md), [`user/getMyInfo.md`](user/getMyInfo.md)
- [`profile/getMyProfile.md`](profile/getMyProfile.md), [`profile/getProfile.md`](profile/getProfile.md), [`profile/updateMyProfile.md`](profile/updateMyProfile.md), [`profile/listSavedClips.md`](profile/listSavedClips.md)

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
> 마지막 갱신: PR #17 머지 (2026-04-27 17:38) — auth 도메인 5/5 완료.

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

- 누적 17 PR (PR4 본 트랙) + 1 PR (#15 docs) / **23h 16m 실측**, 잠·식사 약 ~9h 제외 시 **실작업 약 14-15h**
- **평균 1.4h/PR** (AI 협업 페이스 — PR3 시리즈에서 1.5h/PR → PR4 시리즈에서 1.4h/PR 로 미세 가속)

### 일반 개발 페이스 대비 배수

| 작업 | 일반 솔로 개발자 | 본 프로젝트 (실측) | 배수 |
|---|---|---|---|
| 멀티모듈 + contracts + common + skeleton 셋업 | 1-2일 | 3h 13m | **4-12×** |
| `common-auth-jwt` 라이브러리 (RS256 + verifier + blacklist hook) | 2-3일 | 약 15m + 사전 PRD 작업 | **20-30×** |
| OAuth2 + PKCE + JWT 발급 모듈 (start/callback/exchange) | 2-4주 | 약 1일 (PR3 시리즈) | **8-15×** |
| Refresh rotation + reuse detection + sid blacklist + logout | 1-2주 | **2h 54m (PR4 시리즈, #14→#17)** | **~14-28×** |
| 단일 CRUD API (Domain~Presentation+테스트) | 0.5-1일 | 1.4h | **4-8×** |

- 품질 신호 양호: 모든 PR 에 의사결정 박제(ADR/Decision Log), ArchUnit 룰 통과, multi-agent 리뷰(code/test/security/ddd-architect) 트레일 일관 유지.
- 속도-품질 trade-off 가 발생했다는 지표(테스트 커버리지 누락, `@Disabled`, ArchUnit 우회 등) 는 현재까지 발견되지 않음.

### 남은 작업 추정 (현 페이스 유지 + 단순 CRUD 가정)

| 범위 | API 수 | 추정 PR | 추정 시간 | 상태 |
|---|---|---|---|---|
| ~~auth refresh+logout (PR4-a/b/c)~~ | ~~2~~ | ~~3~~ | ~~2h 54m~~ | ✅ 완료 (#14, #16, #17) |
| user + profile | 8 | 8-12 | ~12-18h | 다음 진입 |
| diary 계열 (diary+comment+validation+diarychat+sentence-feedback) | 24 | 24-30 | ~36-45h | profile 후 |
| chat | 14 | 14-18 | ~21-27h | diary 후 |
| learning (sentence + word) | 8 | 8-10 | ~12-15h | — |
| platform (shorts + event + feedback) | 3 | 3 | ~5h | — |
| **남은 합계** | **57** | **~57-73** | **~86-110h ≈ 영업일 11-14일** | |

후속 별도 PR (PR4-c security/test 리뷰 deferral, decisions/auth/presentation-error-policy.md):
- Security H1 (Spring Security default-deny), H2 (rate limiting), M1 (refresh transport 결정), M2 (ArchUnit 호출자 룰), M3 (cause class logging), M5 (deviceId binding)
- Code M1 (AuthErrorCode 그룹화), M2 (AuthExchangeResponse.from 팩토리), M3 (common-auth-web 모듈 분리)

추정의 한계:
- AI 의존 chat API (generateChat, createAnswer 등 6-7개) 는 ai-service 진척도에 종속.
- 인증·권한이 얽힌 도메인(diarychat 의 권한 검증, sentence-feedback 의 비동기 워크플로) 은 단순 CRUD 가정보다 길어질 수 있음.
- diary 진입 시점에 Code M3 (`common-auth-web`) 추출이 자연스러움 (cross-service 첫 보호 endpoint 등장).

## 관련 문서

- [Service ↔ Domain Mapping](../architecture/service-domain-mapping.md)
- [Contracts Catalog](../architecture/contracts-catalog.md)
- ADR: [`docs/adr/`](../adr/)
