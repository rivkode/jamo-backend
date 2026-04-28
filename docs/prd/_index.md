# Legacy API PRD 인덱스

> 출처: `~/dev/project/kotlin/language` (Kotlin/Spring) — 25개 컨트롤러 / **58개 endpoint** (mined)
> 각 endpoint = 1 PRD. 위치: `docs/prd/{domain}/{handler}.md`
> 상태: `status: mined`(현재) → `verified`(Phase 0.5 KEEP/DROP/FIX 완료) → `frozen`(read-only)
> 그린필드 신규 PRD (`proposed`) 는 본 인덱스에 포함하지 않음 — `_status.md` 참조.

## 도메인별 endpoint 개수 (mined)

| Domain | Endpoints |
|---|---|
| auth | 5 |
| chat | 14 |
| comment | 4 |
| diary | 6 |
| diarychat | 9 |
| event | 1 |
| feedback | 1 |
| profile | 3 |
| sentence | 4 |
| shorts | 1 |
| user | 4 |
| validation | 2 |
| word | 4 |
| **합계** | **58** |

## 전체 PRD 목록

### auth (5)
- [POST /api/v1/auth/exchange](auth/exchange.md)
- [POST /api/v1/auth/logout](auth/logout.md)
- [POST /api/v1/auth/refresh](auth/refresh.md)
- [GET /api/v1/auth/oauth/{provider}/start](auth/start.md)
- [GET /api/v1/auth/oauth/{provider}/callback](auth/callback.md)

### chat (14)
- [GET /api/v1/chat/hello](chat/hello.md)
- [POST /api/v1/chat](chat/createChat.md)
- [POST /api/v1/chat/generate](chat/generateChat.md)
- [POST /api/v1/chat/transcribe](chat/transcribeChat.md)
- [POST /api/v1/chat/speech](chat/speechAudio.md)
- [POST /api/v1/chat/greeting](chat/greeting.md)
- [POST /api/v1/chat/paraphrase](chat/phrase.md)
- [GET /api/v1/chatrooms](chat/listChatRooms.md)
- [POST /api/v1/chatrooms](chat/createChatRoom.md)
- [GET /api/v1/chatrooms/{chatRoomId}/chat](chat/listChats.md)
- [GET /api/v1/answers/{questionId}](chat/getAnswer.md)
- [POST /api/v1/answers](chat/createAnswer.md)
- [POST /api/v1/questions](chat/createQuestion.md)
- [GET /api/v1/questions/me](chat/listMyQuestions.md)

### comment (4)
- [POST /api/v1/comments/{commentId}/like](comment/toggleLike.md)
- [DELETE /api/v1/comments/{commentId}](comment/delete.md)
- [GET /api/v1/diaries/{diaryId}/comments](comment/list.md)
- [POST /api/v1/diaries/{diaryId}/comments](comment/create.md)

### diary (6)
- [GET /api/v1/diaries/feed](diary/listFeed.md)
- [GET /api/v1/diaries/me](diary/listMyFeed.md)
- [GET /api/v1/diaries/{diaryId}](diary/get.md)
- [POST /api/v1/diaries](diary/create.md)
- [DELETE /api/v1/diaries/{diaryId}](diary/delete.md)
- [POST /api/v1/diaries/{diaryId}/like](diary/toggleLike.md)

### diarychat (9)
- [GET /api/v1/diary-chatrooms/{roomId}/messages](diarychat/listMessages.md)
- [POST /api/v1/diary-chatrooms/{roomId}/messages](diarychat/send.md)
- [GET /api/v1/diary-chatrooms/{roomId}/messages/poll](diarychat/poll.md)
- [POST /api/v1/diary-chatrooms](diarychat/create.md)
- [GET /api/v1/diary-chatrooms/{roomId}](diarychat/get.md)
- [GET /api/v1/diary-chatrooms/{roomId}/participants](diarychat/listParticipants.md)
- [POST /api/v1/diary-chatrooms/{roomId}/join](diarychat/join.md)
- [POST /api/v1/diary-chatrooms/{roomId}/leave](diarychat/leave.md)
- [POST /api/v1/diary-chatrooms/{roomId}/ai-toggle](diarychat/aiToggle.md)

### event (1)
- [POST /api/v1/events](event/createEvent.md)

### feedback (1)
- [POST /api/v1/feedbacks](feedback/createFeedback.md)

### profile (3)
- [GET /api/v1/profiles/me](profile/getMyProfile.md)
- [GET /api/v1/profiles/{userId}](profile/getProfile.md)
- [PATCH /api/v1/profiles/me](profile/updateMyProfile.md)

### sentence (4)
- [POST /api/v1/sentences](sentence/createSentence.md)
- [GET /api/v1/sentences/me](sentence/listMySentences.md)
- [GET /api/v1/sentences/me/{sentenceId}](sentence/getMySentence.md)
- [GET /api/v1/sentences/{sentenceId}/feedback](sentence/getFeedback.md)

### shorts (1)
- [GET /api/v1/shorts/feed](shorts/listFeed.md)

### user (4)
- [POST /api/v1/users](user/createUser.md)
- [POST /api/v1/users/validation-number](user/sendValidationNumber.md)
- [POST /api/v1/users/validation-email](user/validateEmail.md)
- [GET /api/v1/users/me](user/getMyInfo.md)

### validation (2)
- [POST /api/v1/diaries/validate](validation/validate.md)
- [POST /api/v1/diaries/validate/line](validation/validateLine.md)

### word (4)
- [GET /api/v1/words/me](word/listMyWords.md)
- [GET /api/v1/words/choice](word/getChoiceWord.md)
- [POST /api/v1/words/choice](word/createChoiceWord.md)
- [GET /api/v1/words/review/{wordListId}](word/listReviewWords.md)
