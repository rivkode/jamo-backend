 ---
Jamo Backend API 명세 — stack/jamo-migration

- Base URL: http://localhost:8080/api/v1 (dev) · https://jamoai.app/api/v1 (prod)
- Auth: Authorization: Bearer {accessToken} (AuthenticatedHttpClient가 자동 주입, 401 시 자동
  refresh)
- 상태 표기: LIVE(클라+백 모두 살아있음) / MOCK(클라가 인메모리 mock으로만 호출) / PENDING(스펙만
  있고 클라 미구현)

0. OAuth & 토큰

#: 0.1
Method: GET
Path: /auth/oauth/{provider}/start
상태: LIVE
Body / Query: path: provider=google|kakao|naver
Response: 302 → 프로바이더 → http://localhost:3000/auth/callback?code=<uuid>
비고: code TTL 60s, 1회용
────────────────────────────────────────
#: 0.2
Method: POST
Path: /auth/exchange
상태: LIVE
Body / Query: {"code":"<uuid>"}
Response:
{"accessToken":"eyJ...","refreshToken":"<uuid>","tokenType":"Bearer","expiresIn":432000}
비고: 400 {"code":"OAUTH_CODE_EXPIRED"}
────────────────────────────────────────
#: 0.3
Method: POST
Path: /auth/refresh
상태: LIVE
Body / Query: {"refreshToken":"<uuid>"}
Response: 0.2와 동일 형식
비고: 401 {"code":"REFRESH_TOKEN_INVALID"} → auto logout
────────────────────────────────────────
#: 0.4
Method: POST
Path: /auth/logout
상태: LIVE
Body / Query: (빈 body)
Response: 200/204
비고: 클라는 응답 무관하게 토큰 삭제

1. User

#: 1.1
Method: POST
Path: /users/login               
상태: LIVE
Body: {"email","password"}       
Response: 200 + 헤더 Authorization: Bearer ...
비고: 401 INVALID_CREDENTIALS
────────────────────────────────────────                        
#: 1.2
Method: POST
Path: /users
상태: LIVE   
Body: {"email","password","username"}
Response: 200/201
비고: 400 EMAIL_NOT_VERIFIED · 409 EMAIL_ALREADY_EXISTS
────────────────────────────────────────                        
#: 1.3
Method: POST
Path: /users/validation-number   
상태: LIVE
Body: {"email"}
Response: 200
비고: OTP 이메일 발송
────────────────────────────────────────
#: 1.4
Method: POST
Path: /users/validation-email
상태: LIVE
Body: {"email","code"}
Response: 200
비고: OTP 검증
────────────────────────────────────────
#: 1.5
Method: GET
Path: /users/me
상태: LIVE
Body: —
Response:
{"id","userId","email","username","avatarUrl","provider":"google|kakao|naver|email","createdAt"}
비고:
────────────────────────────────────────
#: 1.6
Method: GET
Path: /users/{userId}
상태: PENDING
Body: path: userId
Response: 1.5 + diaryCount,followerCount,followingCount,isFollowing,bio
비고: 404 USER_NOT_FOUND

2. Diary (3줄 일기)

┌─────┬─────┬─────────────┬─────┬────────────────────────────────┬───────────────────────────┐
│  #  │ Met │    Path     │ 상  │          Body / Query          │         Response          │
│     │ hod │             │ 태  │                                │                           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │     │ /diaries/fe │ MOC │ query: cursor, size=10,        │ {"items":[Diary],"paging" │
│ 2.1 │ GET │ ed          │ K   │ sort=recent|trending, category │ :{"nextCursor","hasNext"} │
│     │     │             │     │                                │ }                         │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│ 2.2 │ GET │ /diaries/{d │ MOC │ path: diaryId                  │ Diary · 404               │
│     │     │ iaryId}     │ K   │                                │ DIARY_NOT_FOUND           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │     │             │     │ {"lines":[s1,s2,s3],"tags":["# │ Diary · 422               │
│ 2.3 │ POS │ /diaries    │ MOC │ 태그",...max5],"isPublic":true │ INVALID_LINE_COUNT · 400  │
│     │ T   │             │ K   │ } (lines 정확히 3개, 각        │ INVALID_LINE_LENGTH       │
│     │     │             │     │ 1~200자)                       │                           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │     │ /diaries/{d │ PEN │                                │ Diary · 403               │
│ 2.4 │ PUT │ iaryId}     │ DIN │ 2.3과 동일                     │ DIARY_FORBIDDEN           │
│     │     │             │ G   │                                │                           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │ DEL │ /diaries/{d │ PEN │                                │                           │
│ 2.5 │ ETE │ iaryId}     │ DIN │ —                              │ 200/204                   │
│     │     │             │ G   │                                │                           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │     │             │ PEN │                                │                           │
│ 2.6 │ GET │ /diaries/me │ DIN │ cursor, size=10                │ 2.1과 동일 (private 포함) │
│     │     │             │ G   │                                │                           │
├─────┼─────┼─────────────┼─────┼────────────────────────────────┼───────────────────────────┤
│     │ POS │ /diaries/{d │ MOC │                                │ {"diaryId","likeCount","u │
│ 2.7 │ T   │ iaryId}/lik │ K   │ {"liked":true/false}           │ serLiked"} (멱등)         │
│     │     │ e           │     │                                │                           │
└─────┴─────┴─────────────┴─────┴────────────────────────────────┴───────────────────────────┘

Diary 스키마
{
"diaryId": 1,
"author": {"userId": 101, "username": "Minji", "avatarUrl": null},
"lines": ["L1","L2","L3"],
"createdAt": "2026-04-24T07:30:00Z",
"tags": ["#비오는날"],
"likeCount": 12,
"commentCount": 5,
"voiceParticipantCount": 3,
"userLiked": false,
"isPublic": true,
"accentTone": "cream"
}

3. Diary Comments

┌─────┬─────┬──────────────────┬──────┬────────────────────────────┬────────────────────────┐
│  #  │ Met │       Path       │ 상태 │        Body / Query        │        Response        │
│     │ hod │                  │      │                            │                        │
├─────┼─────┼──────────────────┼──────┼────────────────────────────┼────────────────────────┤
│ 3.1 │ GET │ /diaries/{diaryI │ LIVE │ cursor, size=20 (max 100)  │ {"items":[Comment],"ne │
│     │     │ d}/comments      │      │                            │ xtCursor","hasNext"}   │
├─────┼─────┼──────────────────┼──────┼────────────────────────────┼────────────────────────┤
│ 3.2 │ POS │ /diaries/{diaryI │ LIVE │ {"text":"...1~500자","pare │ Comment (201)          │
│     │ T   │ d}/comments      │      │ ntCommentId":null}         │                        │
├─────┼─────┼──────────────────┼──────┼────────────────────────────┼────────────────────────┤
│ 3.3 │ POS │ /comments/{comme │ LIVE │ {"liked":true/false}       │ {"commentId","likeCoun │
│     │ T   │ ntId}/like       │      │                            │ t","userLiked"}        │
├─────┼─────┼──────────────────┼──────┼────────────────────────────┼────────────────────────┤
│ 3.4 │ DEL │ /comments/{comme │ LIVE │ —                          │ 204                    │
│     │ ETE │ ntId}            │      │                            │                        │
└─────┴─────┴──────────────────┴──────┴────────────────────────────┴────────────────────────┘

▎ §3 백엔드 정합 (2026-05-26, Slice 1):
▎  - 3.1 응답은 평탄 구조 ({items, nextCursor, hasNext}) — FeedResponse 정합. paging 객체 nested 노출은 Slice 2 alias.
▎  - 3.1 size: default 20, max 100 (Application 검증).
▎  - 3.2 정상 응답 201. UUID 기반 식별자 (PRD 예시 정수 1001은 mock — 실제는 UUID 문자열).
▎  - 3.4 응답은 204 No Content.
▎  - author.avatarUrl / parentCommentId 가 null 인 경우 응답에 키 명시 노출 ("avatarUrl": null) — Jackson Include.ALWAYS.
▎  - diaryId 정수 ↔ UUID, lines[3] ↔ content 차이는 Slice 2 alias / 프론트 변환으로 해소 (plan 박제).

Comment 스키마
{
"commentId": 1001,
"diaryId": 1,
"author": {"userId": 201, "username": "David", "avatarUrl": null},
"text": "Great!",
"createdAt": "2026-04-24T08:00:00Z",
"parentCommentId": null,
"likeCount": 2,
"userLiked": false
}

4. Diary AI Validation (게시 전 3줄 첨삭)

#: 4.1
Method: POST
Path: /diaries/validate
상태: MOCK
Body: {"lines":[s1,s2,s3]}
Response: {"validationId":"val-...","lines":[{"lineIndex","originalText","status":"ok|suggestion|
error","message","suggestion"}]}
────────────────────────────────────────
#: 4.2
Method: POST
Path: /diaries/validate/line
상태: MOCK
Body: {"lineIndex","text"}
Response: 단일 line 검증 결과
────────────────────────────────────────
#: 4.3
Method: GET
Path: /diaries/validate/{validationId}
상태: PENDING
Body: —
Response: {"status":"pending|completed|failed","lines":[...]} (async 패턴용, v1은 동기)

- 모든 line status≠error여야 POST /diaries 허용
- 동기 응답 SLA <2s (mock 600ms)

5. Diary Chatroom (음성→텍스트, 롱폴링)

▎ 별도 base path /diary-chatrooms — 기존 /chatrooms(페르소나)와 분리됨

#: 5.1
Method: POST
Path: /diary-chatrooms               
상태: LIVE
Body / Query: {"diaryId","aiAssistantEnabled":false}
Response: DiaryChatRoom (다이어리당 1개, 멱등)
────────────────────────────────────────
#: 5.2
Method: GET
Path: /diary-chatrooms/{roomId}
상태: LIVE
Body / Query: —
Response: DiaryChatRoom · 404 CHATROOM_NOT_FOUND
────────────────────────────────────────
#: 5.3
Method: GET
Path: /diary-chatrooms/{roomId}/participants
상태: LIVE
Body / Query: —
Response: {"items":[{"user","isHost","joinedAt"}]}
────────────────────────────────────────   
#: 5.4
Method: POST
Path: /diary-chatrooms/{roomId}/join
상태: LIVE
Body / Query: —
Response: DiaryChatRoom
────────────────────────────────────────
#: 5.5
Method: POST
Path: /diary-chatrooms/{roomId}/leave
상태: LIVE
Body / Query: —
Response: 200/204
────────────────────────────────────────
#: 5.6
Method: POST
Path: /diary-chatrooms/{roomId}/ai-toggle
상태: LIVE
Body / Query: {"enabled":true}
Response: DiaryChatRoom · 403 CHATROOM_FORBIDDEN(host만)
────────────────────────────────────────
#: 5.7
Method: GET
Path: /diary-chatrooms/{roomId}/messages
상태: LIVE
Body / Query: before(messageId), size=30
Response: {"items":[Message],"hasMore","oldestMessageId"}
────────────────────────────────────────
#: 5.8
Method: GET
Path: /diary-chatrooms/{roomId}/messages/poll
상태: LIVE
Body / Query: after(lastId), wait=25(max 60)
Response: {"items":[Message],"events":[{"type":"participant_joined|participant_left|ai_toggle_cha
nged",...}],"nextAfter"}
— 새 메시지 즉시, 없으면 wait초 대기 후 빈 배열 (절대 408 금지)
────────────────────────────────────────
#: 5.9
Method: POST
Path: /diary-chatrooms/{roomId}/messages
상태: LIVE
Body / Query: {"text","audioUrl":null}
Response: Message

DiaryChatRoom 스키마
{"roomId":7,"diaryId":1,"hostUserId":101,"aiAssistantEnabled":false,"participantCount":1,"created
At":"..."}

Message 스키마
{"messageId":42,"roomId":7,"author":{...},"text":"...","audioUrl":null,"createdAt":"...","source"
:"user|ai|system"}

Client 타임아웃: wait + 10s 필요. 폴링은 응답 받으면 즉시 다음 폴링.

6. Audio (STT / TTS) — 재사용

┌─────┬───────┬───────────────┬─────┬──────────────────┬──────────────────────┬─────────────┐
│  #  │ Metho │     Path      │ 상  │   Content-Type   │         Body         │  Response   │
│     │   d   │               │ 태  │                  │                      │             │
├─────┼───────┼───────────────┼─────┼──────────────────┼──────────────────────┼─────────────┤
│ 6.1 │ POST  │ /chat/transcr │ LIV │ multipart/form-d │ audio(file .wav),    │ {"text":".. │
│     │       │ ibe           │ E   │ ata              │ chatRoomId(opt int)  │ ."}         │
├─────┼───────┼───────────────┼─────┼──────────────────┼──────────────────────┼─────────────┤
│ 6.2 │ POST  │ /chat/speech  │ LIV │ application/json │ {"text","language":" │ 오디오 파일 │
│     │       │               │ E   │                  │ ko"}                 │  또는 URL   │
└─────┴───────┴───────────────┴─────┴──────────────────┴──────────────────────┴─────────────┘

다이어리 채팅방의 음성 메시지는 Audio 녹음 → /chat/transcribe → text 획득 →
/diary-chatrooms/{id}/messages 순서.

7. Legacy (페르소나·문장·단어·클립·쇼츠) — 유지

┌────────────┬────────────────────────────────────────────────────────────────────────┬──────┐
│  카테고리  │                                Endpoint                                │ 상태 │
├────────────┼────────────────────────────────────────────────────────────────────────┼──────┤
│ Persona    │ GET/POST /chatrooms, GET /chatrooms/{id}/chat, POST /chat, POST        │ LIVE │
│ chat       │ /chat/generate, POST /chat/paraphrase, POST /chat/greeting             │      │
├────────────┼────────────────────────────────────────────────────────────────────────┼──────┤
│ Sentence   │ POST /sentences, GET /sentences/me, GET /sentences/today-expression,   │ LIVE │
│            │ POST /feedbacks, GET /sentences/{id}/feedback                          │      │
├────────────┼────────────────────────────────────────────────────────────────────────┼──────┤
│ Word       │ GET /words/choice?part&lastWordId                                      │ LIVE │
├────────────┼────────────────────────────────────────────────────────────────────────┼──────┤
│ Clip       │ GET /clip-learning/feed, /transcript, /clips/{id}, POST                │ LIVE │
│ learning   │ /clips/{id}/save, POST /progress                                       │      │
├────────────┼────────────────────────────────────────────────────────────────────────┼──────┤
│ Shorts     │ GET /shorts/feed, POST /shorts/{id}/save                               │ LIVE │
└────────────┴────────────────────────────────────────────────────────────────────────┴──────┘

▎ 다이어리 피벗의 핵심 동선과 직접 관계 없는 레거시. 백엔드 작업 우선순위 낮음.

  ---
백엔드 작업 우선순위 요약

┌──────┬────────────────────────────────────────────┬────────────────────────────────────────┐
│ 우선 │                    영역                    │              이유 / 진행 상황          │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ DONE │ Diary CRUD (2.1~2.3, 2.5~2.7),             │ 백엔드 LIVE. UUID 식별자 / content 단일│
│      │ Comments (3.1~3.4)                         │ 문자열은 프론트가 변환. (Slice 1, 2026-│
│      │                                            │ 05-26)                                 │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P0   │ 명세 정합 alias (path/field) — Slice 2     │ /users/login,/users/me,/users/{id} path│
│      │                                            │ alias + isPublic/userLiked/username/   │
│      │                                            │ provider/expiresIn field alias         │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P1   │ AI Validation (4.1, 4.2)                   │ sentence-feedback 과 의미 다른 fast-   │
│      │                                            │ path 신규 — 후속 plan                  │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P1   │ Diary Chatroom 전체 (5.1~5.9)              │ 클라는 실제 API 호출 중인데 백         │
│      │                                            │ 미구현이면 404. 음성 채팅방 동작 안 함 │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P1   │ Diary edit (2.4), Profile diaryCount (1.6) │ Slice 3 — Aggregate update + gRPC      │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P2   │ User profile follower* (1.6 잔여)          │ follower 도메인 신규                   │
├──────┼────────────────────────────────────────────┼────────────────────────────────────────┤
│ P3   │ Validation async polling (4.3)             │ v1은 동기로 충분                       │
└──────┴────────────────────────────────────────────┴────────────────────────────────────────┘

  ---
OAuth 플로우 (웹) — 백엔드가 알아야 할 점

1. 클라가 launchUrl(GET /auth/oauth/{provider}/start)로 새 탭 오픈
2. 프로바이더 인증 후 백엔드가 http://localhost:3000/auth/callback?code=<uuid> 로 리다이렉트 (TTL
   60s)
3. Flutter 웹 콜백 화면이 code 추출 → POST /auth/exchange
4. 401 발생 시 AuthenticatedHttpClient가 자동으로 /auth/refresh 시도 후 원 요청 재시도

▎ 포트 3000 고정 — 다른 포트면 콜백이 도착 못 하고 code 소진됨. 현재 Docker도 localhost:3000으로
▎ 띄워놓음.

  ---
지금 http://localhost:3000 에 떠 있는 게 진짜 최신(stack/jamo-migration) 버전이고, 위 명세가 그
코드 + docs/prd/(three_line_diary_prd.md, api_audit.md, backend_api_request.md) 대조 결과입니다.
백엔드 에이전트에는 위 표를 그대로 붙여넣으면 됩니다.