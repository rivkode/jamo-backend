# Legacy API PRD 인덱스

> 출처: `~/dev/project/kotlin/language` (Kotlin/Spring) — 25개 컨트롤러 / **65개 endpoint**
> 각 endpoint = 1 PRD. 위치: `legacy-specs/{domain}/{handler}.md`
> 상태: `status: mined`(현재) → `verified`(Phase 0.5 KEEP/DROP/FIX 완료) → `frozen`(read-only)

## 도메인별 endpoint 개수

| Domain | Endpoints |
|---|---|
| auth | 5 |
| chat | 14 |
| cliplearning | 5 |
| comment | 4 |
| diary | 6 |
| diarychat | 9 |
| event | 1 |
| feedback | 1 |
| profile | 4 |
| sentence | 4 |
| shorts | 1 |
| user | 5 |
| validation | 2 |
| word | 4 |
| **합계** | **65** |

## 전체 PRD 목록

### auth (5)
- [POST /api/v1/auth/exchange](auth/exchange.md)
- [POST /api/v1/auth/logout](auth/logout.md)
- [POST /api/v1/auth/refresh](auth/refresh.md)
- [GET /api/v1/auth/oauth/{provider}/start](auth/start.md)
- [GET /api/v1/auth/oauth/{provider}/callback](auth/callback.md)

### chat (14)
- [GET /api/v1/chat/hello](chat/hello.md)
- [POST /api/v1/chat](chat/registerChat.md)
- [POST /api/v1/chat/generate](chat/generateChat.md)
- [POST /api/v1/chat/transcribe](chat/transcribeChat.md)
- [POST /api/v1/chat/speech](chat/speechAudio.md)
- [POST /api/v1/chat/greeting](chat/greeting.md)
- [POST /api/v1/chat/paraphrase](chat/phrase.md)
- [GET /api/v1/chatrooms](chat/retrieveChatRoomList.md)
- [POST /api/v1/chatrooms](chat/registerChatRoom.md)
- [GET /api/v1/chatrooms/{chatRoomId}/chat](chat/retrieveChatList.md)
- [GET /api/v1/answers/{questionId}](chat/retrieveAnswer.md)
- [POST /api/v1/answers](chat/registerAnswer.md)
- [POST /api/v1/questions](chat/registerQuestion.md)
- [GET /api/v1/questions/me](chat/retrieveMyQuestionList.md)

### comment (4)
- [POST /api/v1/comments/{commentId}/like](comment/toggleLike.md)
- [DELETE /api/v1/comments/{commentId}](comment/delete.md)
- [GET /api/v1/diaries/{diaryId}/comments](comment/list.md)
- [POST /api/v1/diaries/{diaryId}/comments](comment/create.md)

### diary (6)
- [GET /api/v1/diaries/feed](diary/getFeed.md)
- [GET /api/v1/diaries/me](diary/getMyFeed.md)
- [GET /api/v1/diaries/{diaryId}](diary/getDetail.md)
- [POST /api/v1/diaries](diary/create.md)
- [DELETE /api/v1/diaries/{diaryId}](diary/delete.md)
- [POST /api/v1/diaries/{diaryId}/like](diary/toggleLike.md)

### diarychat (9)
- [GET /api/v1/diary-chatrooms/{roomId}/messages](diarychat/history.md)
- [POST /api/v1/diary-chatrooms/{roomId}/messages](diarychat/send.md)
- [GET /api/v1/diary-chatrooms/{roomId}/messages/poll](diarychat/poll.md)
- [POST /api/v1/diary-chatrooms](diarychat/create.md)
- [GET /api/v1/diary-chatrooms/{roomId}](diarychat/get.md)
- [GET /api/v1/diary-chatrooms/{roomId}/participants](diarychat/participants.md)
- [POST /api/v1/diary-chatrooms/{roomId}/join](diarychat/join.md)
- [POST /api/v1/diary-chatrooms/{roomId}/leave](diarychat/leave.md)
- [POST /api/v1/diary-chatrooms/{roomId}/ai-toggle](diarychat/aiToggle.md)

### event (1)
- [POST /api/v1/events](event/events.md)

### feedback (1)
- [POST /api/v1/feedbacks](feedback/registerFeedback.md)

### profile (4)
- [GET /api/v1/profiles/me](profile/retrieveMyProfile.md)
- [GET /api/v1/profiles/{userId}](profile/retrieveProfile.md)
- [GET /api/v1/profiles/{userId}/saved-clips](profile/retrieveSavedClips.md)
- [PATCH /api/v1/profiles/me](profile/updateMyProfile.md)

### sentence (4)
- [POST /api/v1/sentences](sentence/registerSentence.md)
- [GET /api/v1/sentences/me](sentence/retrieveMySentenceList.md)
- [GET /api/v1/sentences/me/{sentenceId}](sentence/retrieveMySentence.md)
- [GET /api/v1/sentences/{sentenceId}/feedback](sentence/retrieveFeedback.md)

### shorts (1)
- [GET /api/v1/shorts/feed](shorts/retrieveFeed.md)

### user (5)
- [POST /api/v1/users](user/registerUser.md)
- [POST /api/v1/users/validation-number](user/sendValidationNumber.md)
- [POST /api/v1/users/validation-email](user/validateEmail.md)
- [GET /api/v1/users/me](user/getMyInfo.md)
- [GET /api/v1/users/{userId}](user/getUserProfile.md)

### validation (2)
- [POST /api/v1/diaries/validate](validation/validate.md)
- [POST /api/v1/diaries/validate/line](validation/validateLine.md)

### word (4)
- [GET /api/v1/words/me](word/retrieveMyWord.md)
- [GET /api/v1/words/choice](word/retrieveChoiceWord.md)
- [POST /api/v1/words/choice](word/registerChoiceWord.md)
- [GET /api/v1/words/review/{wordListId}](word/retrieveReviewWords.md)
