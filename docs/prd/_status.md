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
| auth | identity-service | 5 | 5 | 0 | - | - | - | ⏳ |
| user | identity-service | 5 | 5 | 0 | - | - | - | ⏳ |
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
| **합계** | | **63** | **60** | **3** | 0 | 0 | 0 | |

## 평가 절차

1. 도메인 첫 use case 착수 직전 — 해당 도메인의 모든 PRD 를 한 번에 KEEP/DROP/FIX 분류
2. 각 PRD 파일 §9 `KEEP/DROP/FIX 분류` 섹션 채움
3. 본 트래커 표 갱신 (커밋에 함께 포함)
4. FIX 의 경우 변경 사항 별도 issue 또는 PR 본문에 기록

## 도메인별 PRD 목록 (참조)

### identity-service
- [`auth/start.md`](auth/start.md), [`auth/callback.md`](auth/callback.md), [`auth/exchange.md`](auth/exchange.md), [`auth/refresh.md`](auth/refresh.md), [`auth/logout.md`](auth/logout.md)
- [`user/registerUser.md`](user/registerUser.md), [`user/sendValidationNumber.md`](user/sendValidationNumber.md), [`user/validateEmail.md`](user/validateEmail.md), [`user/getMyInfo.md`](user/getMyInfo.md), [`user/getUserProfile.md`](user/getUserProfile.md)
- [`profile/retrieveMyProfile.md`](profile/retrieveMyProfile.md), [`profile/retrieveProfile.md`](profile/retrieveProfile.md), [`profile/updateMyProfile.md`](profile/updateMyProfile.md), [`profile/retrieveSavedClips.md`](profile/retrieveSavedClips.md)

### diary-service
- diary: [`diary/create.md`](diary/create.md), [`diary/getDetail.md`](diary/getDetail.md), [`diary/getFeed.md`](diary/getFeed.md), [`diary/getMyFeed.md`](diary/getMyFeed.md), [`diary/delete.md`](diary/delete.md), [`diary/toggleLike.md`](diary/toggleLike.md)
- comment: [`comment/create.md`](comment/create.md), [`comment/list.md`](comment/list.md), [`comment/delete.md`](comment/delete.md), [`comment/toggleLike.md`](comment/toggleLike.md)
- validation: [`validation/validate.md`](validation/validate.md), [`validation/validateLine.md`](validation/validateLine.md)
- diarychat: [`diarychat/create.md`](diarychat/create.md), [`diarychat/get.md`](diarychat/get.md), [`diarychat/join.md`](diarychat/join.md), [`diarychat/leave.md`](diarychat/leave.md), [`diarychat/participants.md`](diarychat/participants.md), [`diarychat/aiToggle.md`](diarychat/aiToggle.md), [`diarychat/send.md`](diarychat/send.md), [`diarychat/history.md`](diarychat/history.md), [`diarychat/poll.md`](diarychat/poll.md)
- sentence-feedback (신규): [`diary/requestSentenceFeedback.md`](diary/requestSentenceFeedback.md), [`diary/acceptSentenceFeedback.md`](diary/acceptSentenceFeedback.md), [`diary/rejectSentenceFeedback.md`](diary/rejectSentenceFeedback.md)

### chat-service
- [`chat/generateChat.md`](chat/generateChat.md), [`chat/greeting.md`](chat/greeting.md), [`chat/hello.md`](chat/hello.md), [`chat/phrase.md`](chat/phrase.md)
- [`chat/registerAnswer.md`](chat/registerAnswer.md), [`chat/registerChat.md`](chat/registerChat.md), [`chat/registerChatRoom.md`](chat/registerChatRoom.md), [`chat/registerQuestion.md`](chat/registerQuestion.md)
- [`chat/retrieveAnswer.md`](chat/retrieveAnswer.md), [`chat/retrieveChatList.md`](chat/retrieveChatList.md), [`chat/retrieveChatRoomList.md`](chat/retrieveChatRoomList.md), [`chat/retrieveMyQuestionList.md`](chat/retrieveMyQuestionList.md)
- [`chat/speechAudio.md`](chat/speechAudio.md), [`chat/transcribeChat.md`](chat/transcribeChat.md)

### learning-service (비배포 시작)
- sentence: [`sentence/registerSentence.md`](sentence/registerSentence.md), [`sentence/retrieveFeedback.md`](sentence/retrieveFeedback.md), [`sentence/retrieveMySentence.md`](sentence/retrieveMySentence.md), [`sentence/retrieveMySentenceList.md`](sentence/retrieveMySentenceList.md)
- word: [`word/registerChoiceWord.md`](word/registerChoiceWord.md), [`word/retrieveChoiceWord.md`](word/retrieveChoiceWord.md), [`word/retrieveMyWord.md`](word/retrieveMyWord.md), [`word/retrieveReviewWords.md`](word/retrieveReviewWords.md)

### platform-service
- shorts: [`shorts/retrieveFeed.md`](shorts/retrieveFeed.md)
- event: [`event/events.md`](event/events.md)
- feedback: [`feedback/registerFeedback.md`](feedback/registerFeedback.md)

## 관련 문서

- [Service ↔ Domain Mapping](../architecture/service-domain-mapping.md)
- [Contracts Catalog](../architecture/contracts-catalog.md)
- ADR: [`docs/adr/`](../adr/)
