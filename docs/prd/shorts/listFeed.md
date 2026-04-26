---
api_id: shorts.retrieveFeed
http_method: GET
path: /api/v1/shorts/feed
auth: Y
controller: ShortsApiController.kt
handler: retrieveFeed
status: mined
---

# GET /api/v1/shorts/feed — 쇼츠 피드 조회

## 1. 요청 (Request)
- Header: `@LoginUser`
- Query: `ShortsFeedDto.FeedRequest` (`@ModelAttribute @Valid`)

## 2. 응답 (Response)
- 성공: `200 OK` + `ShortsFeedDto.FeedResponse`

## 3. 비즈니스 로직 (요약)
1. `shortsService.retrieveFeed(userId, request)` → 피드 응답.

## 4. 데이터 의존
- DB read: shorts 관련 테이블
- 외부: 동영상 스토리지(S3/CDN)

## 5. 예외 케이스
- validation → 400

## 6. 암묵적 로직 (Implicit)
- 클래스에 `@Validated`.
- cliplearning과 별도 도메인 — 둘이 통합 가능성?

## 7. 호출자 (Clients)
- 모바일

## 8. TODO / Open Questions
- [ ] cliplearning과의 도메인 경계 (Phase 2 도메인 모델링에서)

## 9. KEEP/DROP/FIX 분류 (Phase 0.5에서 채움)
