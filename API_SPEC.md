# jamo-backend API 명세 (Frontend 연동용)

> 현재 **실제 구현되어 정상 구동되는** HTTP API 전체 명세입니다.
> 이 문서는 프론트엔드 구현/에이전트의 **단일 소스 오브 트루스(Single Source of Truth)** 입니다.
> 모든 내용은 `*Controller.java` / `*Request.java` / `*Response.java` / ExceptionHandler 코드에서 직접 추출했습니다.
>
> - 생성 기준일: 2026-05-31
> - 브랜치: `feature/identity-diary-count-grpc`
> - 미구현/placeholder 서비스(chat / learning / platform / ai-service)는 제외했습니다.

---

## 0. 공통 규약

### 서비스 / 베이스 URL

| 서비스 | 로컬 베이스 URL | 포함 도메인 |
|---|---|---|
| identity-service | `http://localhost:8081` | auth, oauth, user(가입/검증), profile |
| diary-service | `http://localhost:8082` | diary, comment, like, sentence-feedback |

> 게이트웨이가 없으므로 프론트는 도메인별로 위 두 origin 을 직접 호출합니다.

### 인증 (JWT Bearer)

- 인증 필요 endpoint 는 access token 을 헤더로 전송합니다.
  ```
  Authorization: Bearer <accessToken>
  ```
- access token 은 로그인/교환/갱신 응답의 `accessToken` 필드로 발급됩니다.
- access token 만료(`expiresIn` 초) 시 `POST /api/v1/auth/refresh` 로 갱신합니다.
- 각 endpoint 의 **🔒 인증 필요** 표시를 참고하세요. 표시가 없으면 비인증(public) 호출.
- 인증 실패(헤더 없음/만료/위조)는 모두 `401` + `code: UNAUTHORIZED` 로 통일됩니다 (만료/위조 구분 미제공).

### Content-Type

- 요청 본문이 있으면: `Content-Type: application/json`
- 응답: `application/json` (OAuth 리다이렉트 endpoint 제외)

### 날짜/시간 포맷

- 모든 타임스탬프는 **ISO-8601 UTC** 문자열 (`Instant`). 예: `"2026-05-31T08:30:00Z"`

### 식별자(ID) 타입

- **identity-service**: userId 등은 **UUID 문자열**.
- **diary-service**: diaryId / commentId / feedbackId / suggestionId 모두 **UUID 문자열**.
  (숫자 ID 아님 — path 에 UUID 형식이 아니면 `400`)

### 커서 페이지네이션

목록 조회 응답 공통 구조:

| 필드 | 타입 | 설명 |
|---|---|---|
| `items` | array | 결과 목록 |
| `nextCursor` | string \| null | 다음 페이지 요청에 그대로 넣을 커서 (없으면 `null`) |
| `hasNext` | boolean | 다음 페이지 존재 여부 |

요청은 `?cursor=<nextCursor>&size=<int>`. `cursor` 생략 시 첫 페이지. 커서는 **opaque 문자열**(내부 인코딩, 파싱 금지).

### 공통 에러 응답 포맷

**모든 API** 는 동일한 `{ code, message }` JSON 형태로 에러를 반환합니다.

```json
{
  "code": "DIARY_NOT_FOUND",
  "message": "diary not found"
}
```

- `code`: 도메인별 고정 enum (프론트가 이 코드로 분기/사용자 메시지 처리)
- `message`: generic 메시지 (디버그용, 사용자 노출용 아님 — raw 도메인 메시지/스택은 노출 안 됨)

### 공통 HTTP 상태 코드

| 코드 | 의미 | 발생 상황 |
|---|---|---|
| 200 | OK | 조회 / 수정 / 좋아요 토글 / 피드백 요청·채택 |
| 201 | Created | 회원가입 / 일기 작성 / 댓글 작성 |
| 204 | No Content | 로그아웃 / 이메일 검증 / 일기·댓글 삭제 / 피드백 거부 |
| 400 | Bad Request | Validation 실패, 잘못된 UUID/형식 |
| 401 | Unauthorized | 토큰 없음/만료/무효, 로그인 자격 오류 |
| 404 | Not Found | 대상 없음 (+ 권한 없는 접근도 IDOR 방지로 404) |
| 409 | Conflict | 이메일 중복 |
| 429 | Too Many Requests | 로그인/검증코드 rate limit |

---

# identity-service (`http://localhost:8081`)

## 1. Auth — 인증

### 1.1 LOCAL 로그인
`POST /api/v1/auth/login`

> 성공 시 `device_id` HttpOnly 쿠키가 함께 설정됩니다(신규 디바이스인 경우).

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| email | string | 필수, 이메일 형식, ≤254자 |
| password | string | 필수, 8~72자 |

**Response `200 OK`** (`AuthExchangeResponse`)
```json
{
  "userId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "accessToken": "eyJhbGciOiJSUzI1NiI...",
  "refreshToken": "eyJhbGciOiJSUzI1NiI...",
  "expiresInSeconds": 3600,
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| userId | string(UUID) | 사용자 ID |
| accessToken | string | JWT access token |
| refreshToken | string | refresh token |
| expiresInSeconds | number(long) | access token 만료(초) |
| expiresIn | number(long) | `expiresInSeconds` 의 alias (동일 값) |
| tokenType | string | 항상 `"Bearer"` |

**에러**: `401` `LOGIN_INVALID` (자격 오류), `429` `LOGIN_RATE_LIMITED`, `400` `VALIDATION_FAILED`

---

### 1.2 Auth Code 교환 (OAuth)
`POST /api/v1/auth/exchange`

> OAuth 콜백에서 받은 1회용 `code` 를 토큰으로 교환합니다.

**Request**
```json
{ "code": "abc123def456" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| code | string | 필수 (1회용 authorization code) |

**Response `200 OK`** — `1.1` 과 동일한 `AuthExchangeResponse`.

**에러**: `401` `AUTH_CODE_INVALID` (없음/만료/사용됨), `400` `VALIDATION_FAILED`

---

### 1.3 토큰 갱신
`POST /api/v1/auth/refresh`

> refresh token 으로 새 access/refresh 토큰 발급(로테이션).

**Request**
```json
{ "refreshToken": "eyJhbGciOiJSUzI1NiI..." }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| refreshToken | string | 필수 |

**Response `200 OK`** — `1.1` 과 동일한 `AuthExchangeResponse`.

**에러**: `401` `REFRESH_EXPIRED` (만료), `401` `REFRESH_INVALID` (위조/재사용)

---

### 1.4 로그아웃 🔒
`POST /api/v1/auth/logout`

> **요청 본문 없음.** access token 헤더의 세션을 무효화. refresh 토큰 폐기 + access 블랙리스트.

**Request**: 본문 없음 (`Authorization: Bearer <accessToken>` 만)

**Response `204 No Content`** (본문 없음)

**에러**: `401` `UNAUTHORIZED`

---

## 2. OAuth2 — 브라우저 리다이렉트

> 브라우저/WebView 기반 플로우. 최종적으로 프론트의 `successRedirect` 로 1회용 `code` 가 전달되며,
> 프론트는 이를 `POST /api/v1/auth/exchange` 로 토큰화합니다.

### 2.1 OAuth 로그인 시작
`GET /api/v1/auth/oauth/{provider}/start`

- `{provider}`: `KAKAO` | `NAVER` | `GOOGLE` (대소문자 무관)
- 동작: state 쿠키(+필요 시 device_id 쿠키) 설정 후 provider 인증 페이지로 `302` 리다이렉트.

### 2.2 OAuth 콜백
`GET /api/v1/auth/oauth/{provider}/callback?code=...&state=...`

- provider 가 호출하는 콜백. 결과는 프론트 URL 로 `302` 리다이렉트:
  - **성공**: `{frontendBaseUrl}/auth/callback?code=<1회용code>&isNew=<bool>&truncated=<bool>`
  - **실패**: `{frontendBaseUrl}/auth/error?code=<ERROR_CODE>`
- 실패 `code` 값: `OAUTH_AUTHORIZATION_FAILED` | `OAUTH_STATE_INVALID` | `OAUTH_FLOW_EXPIRED` | `OAUTH_PROVIDER_UNAVAILABLE`
- 이후 프론트는 성공 `code` 로 `POST /api/v1/auth/exchange` 호출.

| 쿼리(성공 redirect) | 타입 | 설명 |
|---|---|---|
| code | string | 토큰 교환용 1회용 코드 |
| isNew | boolean | 신규 가입 사용자 여부 |
| truncated | boolean | displayName 이 길이 제한으로 잘렸는지 |

---

## 3. User — 회원가입 / 이메일 검증

### 3.1 회원가입 (LOCAL)
`POST /api/v1/users`

> 가입 전 이메일 검증(`3.2`, `3.3`)이 선행되어야 합니다. 자동 로그인 없음(토큰 미발급).

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123!",
  "displayName": "홍길동"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| email | string | 필수, 이메일 형식, ≤254자 |
| password | string | 필수, 8~72자 |
| displayName | string | 필수, 1~32자. **`username` 키로 보내도 매핑됨**(alias) |

**Response `201 Created`**
```json
{
  "userId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "email": "user@example.com",
  "displayName": "홍길동",
  "username": "홍길동",
  "createdAt": "2026-05-31T08:00:00Z"
}
```
> `username` 은 `displayName` 의 alias(동일 값).

**에러**: `409` `EMAIL_ALREADY_REGISTERED`, `400` `EMAIL_NOT_VALIDATED` (이메일 검증 미완료), `400` `VALIDATION_FAILED`

---

### 3.2 이메일 검증코드 발송
`POST /api/v1/users/validation-number`

**Request**
```json
{ "email": "user@example.com" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| email | string | 필수, 이메일 형식, ≤254자 |

**Response `204 No Content`**

**에러**: `429` `VALIDATION_RATE_LIMITED` (30초 쿨다운/1일 한도)

---

### 3.3 이메일 검증코드 확인
`POST /api/v1/users/validation-email`

**Request**
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| email | string | 필수, 이메일 형식 |
| code | string | 필수, 정확히 6자리 숫자 |

**Response `204 No Content`**

**에러**: `400` `VALIDATION_CODE_MISMATCH` / `VALIDATION_CODE_EXPIRED` / `VALIDATION_CODE_LOCKED`

---

## 4. Profile — 프로필

### 4.1 내 프로필 조회 🔒
`GET /api/v1/profiles/me`

**Response `200 OK`** (`MyProfileResponse`)
```json
{
  "id": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "email": "user@example.com",
  "displayName": "홍길동",
  "username": "홍길동",
  "providers": ["GOOGLE"],
  "provider": "GOOGLE",
  "createdAt": "2026-05-01T00:00:00Z",
  "bio": "안녕하세요",
  "avatarUrl": "https://cdn.example.com/avatar/1.png",
  "locale": "ko_KR",
  "diaryCount": 12
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| id | string(UUID) | 사용자 ID |
| email | string \| null | 이메일 |
| displayName | string | 표시 이름 |
| username | string | `displayName` alias |
| providers | string[] | 연결된 OAuth provider 목록 (LOCAL 가입 시 빈 배열) |
| provider | string \| null | `providers[0]` alias (없으면 null) |
| createdAt | string(Instant) | 가입 시각 |
| bio | string \| null | 자기소개 |
| avatarUrl | string \| null | 아바타 URL |
| locale | string | 로케일 코드 |
| diaryCount | number(long) \| null | 본인 전체 일기 수(공개+비공개). 집계 실패 시 `null` |

---

### 4.2 타 사용자 프로필 조회 🔒
`GET /api/v1/profiles/{userId}`

- `{userId}`: UUID 문자열. 형식 오류 시 `400 VALIDATION_FAILED`.

**Response `200 OK`** (`PublicProfileResponse`, public-safe)
```json
{
  "id": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "displayName": "홍길동",
  "username": "홍길동",
  "bio": "안녕하세요",
  "avatarUrl": "https://cdn.example.com/avatar/1.png",
  "diaryCount": 8
}
```
> `email` / `providers` / `locale` / `createdAt` 미노출. `diaryCount` 는 **공개 일기 수만**. 집계 실패 시 `null`.

**에러**: `404` `USER_NOT_FOUND`

---

### 4.3 내 프로필 부분 수정 🔒
`PATCH /api/v1/profiles/me`

> 화이트리스트 4필드 부분 수정. **모든 필드 nullable** — `null`/미포함 필드는 변경 없음.

**Request**
```json
{
  "displayName": "새이름",
  "bio": "수정된 자기소개",
  "avatarUrl": "https://cdn.example.com/avatar/2.png",
  "locale": "en_US"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| displayName | string | 선택, 1~32자 (빈 문자열 → 400) |
| bio | string | 선택, ≤200자 (빈 문자열 → null 정규화) |
| avatarUrl | string | 선택, ≤500자 (빈 문자열 → null 정규화) |
| locale | string | 선택, ≤8자 (빈 문자열 → 400) |

**Response `200 OK`** — `4.1` 과 동일한 `MyProfileResponse`.

**에러**: `400` `DISPLAY_NAME_CHANGE_TOO_FREQUENT` (7일 1회 제한), `400` `VALIDATION_FAILED`

---

## 5. User Path Alias (Flutter 경로 정합)

> 아래 3개는 각각 `1.1` / `4.1` / `4.2` 로 **위임**됩니다. 비즈니스 로직/응답은 100% 동일.
> (legacy 호환용 alias, 추후 deprecation 예정 — 신규 구현은 정식 경로 권장)

| Alias | 위임 대상 | 응답 |
|---|---|---|
| `POST /api/v1/users/login` | `POST /api/v1/auth/login` | `AuthExchangeResponse` (`1.1`) |
| `GET /api/v1/users/me` 🔒 | `GET /api/v1/profiles/me` | `MyProfileResponse` (`4.1`) |
| `GET /api/v1/users/{userId}` 🔒 | `GET /api/v1/profiles/{userId}` | `PublicProfileResponse` (`4.2`) |

---

# diary-service (`http://localhost:8082`)

> **이 서비스의 모든 endpoint 는 🔒 인증 필요** (`Authorization: Bearer <accessToken>`).
> 모든 리소스 ID 는 **UUID 문자열**. 권한 없는 접근은 정보 노출 방지를 위해 `404` 로 응답(IDOR 통일).

## 6. Diary — 일기

### 6.1 일기 작성
`POST /api/v1/diaries`

**Request**
```json
{
  "lines": ["오늘은 좋은 날이었다.", "산책을 했다.", "기분이 좋았다."],
  "images": ["https://cdn.example.com/img/1.jpg"],
  "tags": ["일상", "행복"],
  "visibility": "PUBLIC"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| lines | string[] | 필수, **정확히 3개**, 각 1~200자(code points). 개수 위반 시 422 `INVALID_LINE_COUNT`, 길이/blank 위반 시 400 `INVALID_LINE_LENGTH` |
| images | string[] | 선택, 최대 5건, 각 http(s) URL ≤2048자. 생략 시 `[]` |
| tags | string[] | 선택, 최대 10건, 각 1~30자(도메인 기준). 생략 시 `[]` |
| visibility | string | 선택, `PUBLIC` \| `PRIVATE`. 생략/null 시 `PUBLIC` |

**Response `201 Created`** (`DiaryResponse`)

> ⚠️ 평탄(flat) 필드가 SoT 이며, `isPublic` / `userLiked` / `author` 는 동일 값의 **호환 alias** 로 함께 내려갑니다.

```json
{
  "diaryId": "11111111-2222-3333-4444-555555555555",
  "authorId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "authorDisplayName": "홍길동",
  "lines": ["오늘은 좋은 날이었다.", "산책을 했다.", "기분이 좋았다."],
  "images": ["https://cdn.example.com/img/1.jpg"],
  "tags": ["일상", "행복"],
  "visibility": "PUBLIC",
  "likeCount": 0,
  "commentCount": 0,
  "likedByMe": false,
  "createdAt": "2026-05-31T08:30:00Z",
  "isPublic": true,
  "userLiked": false,
  "author": {
    "userId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
    "username": "홍길동",
    "avatarUrl": null
  }
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| diaryId | string(UUID) | 일기 ID |
| authorId | string(UUID) | 작성자 ID |
| authorDisplayName | string | 작성자 표시 이름 |
| lines | string[] | 본문 3줄 |
| images | string[] | 이미지 URL 목록 |
| tags | string[] | 태그 목록 |
| visibility | string | `PUBLIC` \| `PRIVATE` |
| likeCount | int | 좋아요 수 |
| commentCount | int | 댓글 수 |
| likedByMe | boolean | 요청자의 좋아요 여부 |
| createdAt | string(Instant) | 작성 시각 (**updatedAt 없음**) |
| isPublic | boolean | alias: `visibility == "PUBLIC"` |
| userLiked | boolean | alias: `likedByMe` |
| author | object | alias: `{ userId, username(=displayName), avatarUrl(항상 null) }` |

**에러**: `400` `DIARY_VALIDATION_FAILED` (Bean Validation) · `400` `INVALID_LINE_LENGTH` (각 줄 1~200자/blank) · `422` `INVALID_LINE_COUNT` (줄 개수≠3)

---

### 6.2 일기 단건 조회
`GET /api/v1/diaries/{diaryId}`

**Response `200 OK`** — `6.1` 과 동일한 `DiaryResponse`.

**에러**: `404` `DIARY_NOT_FOUND` (없음 또는 비공개+비작성자 접근)

---

### 6.3 공개 피드 조회
`GET /api/v1/diaries/feed?cursor={cursor}&size=10&sort={sort}&tag={tag}`

| 쿼리 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| cursor | string | (없음) | 이전 응답의 `nextCursor` |
| size | int | 10 | 1~100 |
| sort | string | (없음→recent) | `recent` \| `popular` |
| tag | string | (없음) | 태그 필터 |

**Response `200 OK`** (`FeedResponse`)
```json
{
  "items": [ { /* DiaryResponse (6.1 과 동일 구조) */ } ],
  "nextCursor": "eyJpZCI6...",
  "hasNext": true
}
```

---

### 6.4 내 피드 조회
`GET /api/v1/diaries/me?cursor={cursor}&size=10`

> 본인 일기만(공개+비공개), 최신순.

| 쿼리 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| cursor | string | (없음) | 이전 응답의 `nextCursor` |
| size | int | 10 | 1~100 |

**Response `200 OK`** — `6.3` 과 동일한 `FeedResponse`.

---

### 6.5 일기 수정
`PUT /api/v1/diaries/{diaryId}`

> 전체 교체(PUT). 본인 일기만. Request 스키마는 `6.1` 작성과 동일.

**Request**
```json
{
  "lines": ["수정된 줄1", "수정된 줄2", "수정된 줄3"],
  "images": [],
  "tags": ["수정"],
  "visibility": "PRIVATE"
}
```
(필드 제약은 `6.1` 과 동일)

**Response `200 OK`** — `DiaryResponse`.

**에러**: `404` `DIARY_NOT_FOUND` (없음 또는 비작성자), `400` `DIARY_VALIDATION_FAILED`

---

### 6.6 일기 삭제
`DELETE /api/v1/diaries/{diaryId}`

**Response `204 No Content`**

**에러**: `404` `DIARY_NOT_FOUND` (없음 또는 비작성자)

---

### 6.7 일기 좋아요 토글
`POST /api/v1/diaries/{diaryId}/like`

> 단일 토글 endpoint. 명시적 boolean 으로 멱등 처리(같은 값 반복 호출 안전).

**Request**
```json
{ "liked": true }
```
| 필드 | 타입 | 설명 |
|---|---|---|
| liked | boolean | `true`=좋아요, `false`=취소 |

**Response `200 OK`** (`ToggleDiaryLikeResponse`)
```json
{
  "diaryId": "11111111-2222-3333-4444-555555555555",
  "liked": true,
  "likeCount": 4,
  "userLiked": true
}
```
> `userLiked` 는 `liked` 의 alias(동일 값). `likeCount` 는 호출 직후 카운트.

---

## 7. Comment — 댓글

### 7.1 댓글 작성
`POST /api/v1/diaries/{diaryId}/comments`

**Request**
```json
{
  "text": "좋은 일기네요!",
  "parentCommentId": null
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| text | string | 필수. 요청 한도 ≤1000자, 도메인 invariant 1~500 code points |
| parentCommentId | string(UUID) \| null | 대댓글 시 부모 댓글 ID. 일반 댓글은 생략/null |

**Response `201 Created`** (`CommentResponse`)
```json
{
  "commentId": "aaaa1111-2222-3333-4444-555566667777",
  "diaryId": "11111111-2222-3333-4444-555555555555",
  "author": {
    "userId": "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
    "username": "홍길동",
    "avatarUrl": null
  },
  "text": "좋은 일기네요!",
  "createdAt": "2026-05-31T08:35:00Z",
  "parentCommentId": null,
  "likeCount": 0,
  "userLiked": false
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| commentId | string(UUID) | 댓글 ID |
| diaryId | string(UUID) | 소속 일기 ID |
| author | object | `{ userId, username(=displayName), avatarUrl(항상 null) }` |
| text | string | 본문 |
| createdAt | string(Instant) | 작성 시각 |
| parentCommentId | string(UUID) \| null | 부모 댓글(대댓글이 아니면 null) |
| likeCount | int | 좋아요 수 |
| userLiked | boolean | 요청자의 좋아요 여부 |

**에러**: `404` `DIARY_NOT_FOUND`, `400` `COMMENT_VALIDATION_FAILED`

---

### 7.2 댓글 목록 조회
`GET /api/v1/diaries/{diaryId}/comments?cursor={cursor}&size=20`

| 쿼리 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| cursor | string | (없음) | 이전 응답의 `nextCursor` |
| size | int | 20 | 1~100 |

**Response `200 OK`** (`CommentListResponse`)
```json
{
  "items": [ { /* CommentResponse (7.1 과 동일 구조) */ } ],
  "nextCursor": "eyJpZCI6...",
  "hasNext": false
}
```
> 정렬: 최신순(desc).

---

### 7.3 댓글 삭제
`DELETE /api/v1/comments/{commentId}`

> soft delete. 본인 댓글만.

**Response `204 No Content`**

**에러**: `404` `COMMENT_NOT_FOUND` (없음 또는 비작성자)

---

### 7.4 댓글 좋아요 토글
`POST /api/v1/comments/{commentId}/like`

**Request**
```json
{ "liked": true }
```
| 필드 | 타입 | 설명 |
|---|---|---|
| liked | boolean | `true`=좋아요, `false`=취소 |

**Response `200 OK`** (`ToggleCommentLikeResponse`)
```json
{
  "commentId": "aaaa1111-2222-3333-4444-555566667777",
  "likeCount": 3,
  "userLiked": true
}
```
> 댓글 토글 응답은 `userLiked` 필드만 사용(일기 토글의 `liked` 와 다름).

---

## 8. Sentence Feedback — 문장 피드백 (AI 교정 제안)

> 문장 단위로 AI 교정 제안을 요청 → 제안 중 하나를 채택(accept) 또는 거부(reject) 하는 흐름.

### 8.1 문장 피드백 요청
`POST /api/v1/diaries/sentence-feedback`

**Request**
```json
{
  "diaryId": "11111111-2222-3333-4444-555555555555",
  "sentence": "I goes to school.",
  "priorSentences": ["Hello.", "My name is Hong."],
  "tone": "casual"
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| diaryId | string(UUID) \| null | 대상 일기(작성 중 임시 문장이면 null 허용). 보내면 UUID 형식 |
| sentence | string | 필수. 도메인 invariant 1~200 code points (라인 단위 피드백 — 일기 1줄) |
| priorSentences | string[] | 선택, 최대 5건(맥락) |
| tone | string \| null | `casual` \| `formal` \| `neutral` (대소문자 무관, unknown 값은 무시) |

**Response `200 OK`** (`SentenceFeedbackResponse`)
```json
{
  "feedbackId": "fb111111-2222-3333-4444-555555555555",
  "status": "SUGGESTED",
  "originalSentence": "I goes to school.",
  "suggestions": [
    {
      "suggestionId": "sg111111-2222-3333-4444-555555555555",
      "text": "I go to school.",
      "reason": "주어 I 에는 동사원형 go 를 사용합니다.",
      "confidence": 0.95
    }
  ],
  "decisionSuggestionId": null,
  "expiresAt": "2026-06-01T08:40:00Z",
  "processedAt": "2026-05-31T08:40:00Z"
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| feedbackId | string(UUID) | 피드백 Aggregate ID |
| status | string | 상태 (아래 표) |
| originalSentence | string | 입력 문장 echo |
| suggestions[].suggestionId | string(UUID) | 제안 ID |
| suggestions[].text | string | 제안 문장 |
| suggestions[].reason | string | 제안 근거 |
| suggestions[].confidence | number(double) | 제안 신뢰도(0.0~1.0) |
| decisionSuggestionId | string(UUID) \| null | 채택된 제안 ID(ACCEPTED 시), 그 외 null |
| expiresAt | string(Instant) \| null | SUGGESTED 시 +24h, 그 외 echo/null |
| processedAt | string(Instant) | 응답 생성 시각 |

**`status` 값**

| 값 | 의미 |
|---|---|
| `REQUESTED` | 요청 접수(제안 생성 전) |
| `SUGGESTED` | 제안 생성됨(suggestions 채워짐, 24h 내 결정 필요) |
| `ACCEPTED` | 제안 채택됨(final) |
| `REJECTED` | 제안 거부됨(final) |
| `EXPIRED` | 미결정 만료(final) |
| `FAILED` | AI 생성 실패(final, fallback 제안 포함 가능) |

**에러**: `400` `SENTENCE_FEEDBACK_VALIDATION_FAILED`

---

### 8.2 제안 채택
`POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept`

**Request**
```json
{ "suggestionId": "sg111111-2222-3333-4444-555555555555" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| suggestionId | string(UUID) | 필수, 채택할 제안 ID |

**Response `200 OK`** — `8.1` 과 동일한 `SentenceFeedbackResponse` (`status: "ACCEPTED"`, `decisionSuggestionId` 채워짐).

**에러**: `404` `SENTENCE_FEEDBACK_NOT_FOUND`, `400` `SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION` (제안 매칭 실패), `409` `SENTENCE_FEEDBACK_INVALID_TRANSITION` (이미 final 상태), `400` `SENTENCE_FEEDBACK_VALIDATION_FAILED`

---

### 8.3 제안 거부
`POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject`

**Request** (본문 선택 — 생략 가능)
```json
{ "reason": "제안이 부자연스러움" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| reason | string \| null | 선택, ≤500자 |

**Response `204 No Content`**

**에러**: `404` `SENTENCE_FEEDBACK_NOT_FOUND`

---

## 부록 A. Endpoint 빠른 목록

| # | Method | Path | 인증 | 서비스 |
|---|---|---|:---:|---|
| 1.1 | POST | `/api/v1/auth/login` | | identity |
| 1.2 | POST | `/api/v1/auth/exchange` | | identity |
| 1.3 | POST | `/api/v1/auth/refresh` | | identity |
| 1.4 | POST | `/api/v1/auth/logout` | 🔒 | identity |
| 2.1 | GET | `/api/v1/auth/oauth/{provider}/start` | | identity |
| 2.2 | GET | `/api/v1/auth/oauth/{provider}/callback` | | identity |
| 3.1 | POST | `/api/v1/users` | | identity |
| 3.2 | POST | `/api/v1/users/validation-number` | | identity |
| 3.3 | POST | `/api/v1/users/validation-email` | | identity |
| 4.1 | GET | `/api/v1/profiles/me` | 🔒 | identity |
| 4.2 | GET | `/api/v1/profiles/{userId}` | 🔒 | identity |
| 4.3 | PATCH | `/api/v1/profiles/me` | 🔒 | identity |
| 5.1 | POST | `/api/v1/users/login` (alias) | | identity |
| 5.2 | GET | `/api/v1/users/me` (alias) | 🔒 | identity |
| 5.3 | GET | `/api/v1/users/{userId}` (alias) | 🔒 | identity |
| 6.1 | POST | `/api/v1/diaries` | 🔒 | diary |
| 6.2 | GET | `/api/v1/diaries/{diaryId}` | 🔒 | diary |
| 6.3 | GET | `/api/v1/diaries/feed` | 🔒 | diary |
| 6.4 | GET | `/api/v1/diaries/me` | 🔒 | diary |
| 6.5 | PUT | `/api/v1/diaries/{diaryId}` | 🔒 | diary |
| 6.6 | DELETE | `/api/v1/diaries/{diaryId}` | 🔒 | diary |
| 6.7 | POST | `/api/v1/diaries/{diaryId}/like` | 🔒 | diary |
| 7.1 | POST | `/api/v1/diaries/{diaryId}/comments` | 🔒 | diary |
| 7.2 | GET | `/api/v1/diaries/{diaryId}/comments` | 🔒 | diary |
| 7.3 | DELETE | `/api/v1/comments/{commentId}` | 🔒 | diary |
| 7.4 | POST | `/api/v1/comments/{commentId}/like` | 🔒 | diary |
| 8.1 | POST | `/api/v1/diaries/sentence-feedback` | 🔒 | diary |
| 8.2 | POST | `/api/v1/diaries/sentence-feedback/{feedbackId}/accept` | 🔒 | diary |
| 8.3 | POST | `/api/v1/diaries/sentence-feedback/{feedbackId}/reject` | 🔒 | diary |

## 부록 B. 에러 코드 목록 (code 필드 값)

**identity / auth** (`AuthErrorCode`): `VALIDATION_FAILED`, `AUTH_CODE_INVALID`, `LOGIN_INVALID`, `LOGIN_RATE_LIMITED`, `REFRESH_EXPIRED`, `REFRESH_INVALID`, `UNAUTHORIZED`, `OAUTH_AUTHORIZATION_FAILED`, `OAUTH_STATE_INVALID`, `OAUTH_FLOW_EXPIRED`, `OAUTH_PROVIDER_UNAVAILABLE`, `INTERNAL_ERROR`

**identity / user** (`UserErrorCode`): `VALIDATION_FAILED`, `VALIDATION_CODE_MISMATCH`, `VALIDATION_CODE_EXPIRED`, `VALIDATION_CODE_LOCKED`, `VALIDATION_RATE_LIMITED`, `EMAIL_NOT_VALIDATED`, `EMAIL_ALREADY_REGISTERED`, `INTERNAL_ERROR`

**identity / profile** (`ProfileErrorCode`): `VALIDATION_FAILED`, `DISPLAY_NAME_CHANGE_TOO_FREQUENT`, `USER_NOT_FOUND`, `INTERNAL_ERROR`

**diary** (`DiaryErrorCode`): `DIARY_NOT_FOUND`, `DIARY_VALIDATION_FAILED`, `UNAUTHORIZED`, `INTERNAL_ERROR`

**comment** (`CommentErrorCode`): `COMMENT_NOT_FOUND`, `DIARY_NOT_FOUND`, `COMMENT_VALIDATION_FAILED`, `UNAUTHORIZED`, `INTERNAL_ERROR`

**sentence-feedback** (`SentenceFeedbackErrorCode`): `SENTENCE_FEEDBACK_VALIDATION_FAILED`, `SENTENCE_FEEDBACK_NOT_FOUND`, `SENTENCE_FEEDBACK_INVALID_TRANSITION`(409), `SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION`(400), `SENTENCE_FEEDBACK_RATE_LIMITED`(429), `UNAUTHORIZED`, `INTERNAL_ERROR`

## 부록 C. Swagger / OpenAPI (개발 환경 전용)

런타임 자동 문서 (prod 에서는 차단됨):

- identity-service: `http://localhost:8081/swagger` , `http://localhost:8081/v3/api-docs`
- diary-service: `http://localhost:8082/swagger` , `http://localhost:8082/v3/api-docs`

## 부록 D. 열거형(enum) 값 정리

| enum | 값 | 사용처 |
|---|---|---|
| visibility | `PUBLIC`, `PRIVATE` | 일기 작성/수정 |
| OAuth provider | `KAKAO`, `NAVER`, `GOOGLE` | OAuth start/callback path |
| sentence-feedback status | `REQUESTED`, `SUGGESTED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `FAILED` | 피드백 응답 |
| sentence-feedback tone | `casual`, `formal`, `neutral` (대소문자 무관) | 피드백 요청 |
| feed sort | `recent`, `popular` | 공개 피드 정렬 |
