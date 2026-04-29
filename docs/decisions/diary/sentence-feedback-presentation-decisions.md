# Decision: sentence-feedback Presentation layer 구현 결정

- **상태**: Accepted
- **결정일**: 2026-04-29
- **결정자**: jonghun
- **PR**: D-a-5-impl-presentation (`feature/diary-sentence-feedback-impl-presentation`)
- **선행 박제**:
  - [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) (16 항목)
  - [`decisions/diary/sentence-feedback-infra-decisions.md`](sentence-feedback-infra-decisions.md) (10 항목)
  - identity profile presentation 패턴 (PR #46) — `ProfileController` / `LoginUser` / `AuthenticatedUser` / `LoginUserArgumentResolver` / `ProfileExceptionHandler`
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md), [ADR-0005 외래 ID + JPA 연관관계 금지](../../adr/0005-no-jpa-associations.md), [ADR-0008 Hexagonal 금지 + Lombok 정책](../../adr/0008-hexagonal-and-lombok-policy.md)

## 컨텍스트

`D-a-5-impl-domain` (PR #62) + `D-a-5-impl-app` (PR #64) + `D-a-5-impl-infra` (PR #71) 위에 HTTP 외부 노출. 3 endpoint (`POST request / accept / reject`) + Bean Validation + JWT 인증 + rate limit + OpenAPI 의무. diary-service 가 controller 를 처음 갖는 PR — CLAUDE.md "새 서비스 OpenAPI 문서화" 의무 충족.

## 결정

### 1. `@LoginUser` 자체 도입 (common-auth-web 모듈 추출 보류)

| 항목 | 결정 |
|---|---|
| 파일 위치 | `diary-service/src/main/java/.../presentation/web/{LoginUser,AuthenticatedUser,LoginUserArgumentResolver,UnauthorizedException,PresentationWebConfig}.java` |
| 패턴 | identity-service 동일 컴포넌트 단순 복사 (5 파일) |
| `AuthenticatedUser.userId` 타입 | primitive `UUID` — 다른 BC 의 도메인 VO (`identity.UserId`) import 차단 (ADR-0005 / ArchUnit R1) |
| `JwtVerifier` | common-auth-jwt 의존 (이미 보유) |

근거:
- 본 PR 스코프 제한 (presentation slice 단일).
- `common-auth-web` 모듈 추출은 두 번째 도메인 controller 등장 시점 (chat / learning / platform) 에 일괄 — premature abstraction 회피.
- identity 의 PR4-c deferral M3 ([decisions/identity/local-credential-deployment-checklist.md] 외 박제) 후속 항목으로 추적.

거부 옵션:
- (B) `common-auth-web` 모듈 신규 추출 — 본 PR 분량 +1 모듈 + identity 측 마이그레이션 동시 → 스코프 폭증.

### 2. Rate limit — Redis bucket (identity ValidationRateLimiter 패턴 정합)

| 항목 | 결정 |
|---|---|
| port | `domain/repository/SentenceFeedbackRateLimiter` (`canRequest(UUID userId)` / `recordRequest(UUID userId)`) |
| 구현 | `infrastructure/redis/SentenceFeedbackRateLimiterRedisStore` — cooldown(분) + daily 두 키 그룹 |
| 키 | `user:sentence-feedback:cooldown:{userId}` (TTL = cooldown 60s) + `user:sentence-feedback:daily:{userId}` (TTL = 24h) |
| 한도 | 분당 10 / 일일 50 (박제 §11) — envvar 로 운영 재정의 |
| 적용 범위 | request only (chat-service 호출 비용 보호). accept / reject 는 quota 미적용 (사용자가 이미 받은 결정 반영) |
| 진입 시점 | T1 트랜잭션 진입 직전 — 거부 시 DB / Outbox 트래픽 X |
| TOCTOU race | identity 정합 박제 — cooldown 1차 + daily soft cap, atomic CAS / Lua 는 운영 영향 측정 후 결정 |

본 PR 이 diary-service 의 첫 Redis 의존 — `spring-boot-starter-data-redis` 추가.

### 3. XSS escape — 클라이언트 책임 (JSON spec 정합)

| 항목 | 결정 |
|---|---|
| 서버 측 escape | **미적용** — Spring MVC default JSON 직렬화 (Jackson) 가 `<` `>` `&` 등 HTML 특수 문자를 그대로 보존 (JSON spec 정합 + REST API 일반 패턴) |
| 클라 책임 | React `dangerouslySetInnerHTML` / Vue `v-html` 사용 시점에 escape (DOMPurify 등) |
| 후속 | Spring Security 도입 시점 (별 PR) `Content-Security-Policy` / `X-Content-Type-Options: nosniff` 헤더 추가 박제 |

근거: JSON 응답에 HTML escape 적용은 비정상 패턴 — 클라가 다시 unescape 해야 함. XSS 방어는 출력 컨텍스트 (HTML / DOM) 책임.

### 4. Bean Validation — Jakarta Validation API

| 항목 | 결정 |
|---|---|
| 의존성 | `spring-boot-starter-validation` 추가 |
| 적용 | request DTO 의 `@NotBlank` `@Size` `@Pattern` (UUID 정규식) — 1차 거부 |
| 도메인 invariant 와의 정합 | Bean Validation = char (UTF-16) 기준 빠른 거부, 도메인 VO = code points 정확 검증 |

`SentenceText 1..50 cp` 와 정합: `@Size(max=200)` (char 기준 1차 거부 — utf8mb4 max 4 byte/cp × 50 = 200 char 여유) + 도메인이 정확 50 cp 검증.

### 5. Suggestion text truncate 불필요

도메인 `Suggestion.TEXT_MAX_CODE_POINTS = 200` invariant 가 이미 보장. 응답 시 별도 truncate / sanitize 없음.

### 6. Reject 응답 — 204 No Content (Application Result 폐기)

| 항목 | 결정 |
|---|---|
| Application | `SentenceFeedbackResult` 반환 (PRD §8 박제대로 status=REJECTED 가능) |
| Controller | `ResponseEntity.status(NO_CONTENT).build()` — Application 결과 폐기 |
| 근거 | PRD `reject §2` KEEP — 204 명시. HTTP 응답 코드 차이는 Controller 책임 (`SentenceFeedbackResult.from` JavaDoc 박제 정합) |

### 7. ErrorCode 그룹화 — `SENTENCE_FEEDBACK_*` prefix

| 코드 | HTTP | 매핑 예외 |
|---|---|---|
| `SENTENCE_FEEDBACK_VALIDATION_FAILED` | 400 | Bean Validation / `IllegalArgumentException` (UUID parse) / `InvalidSentenceTextException` / `HttpMessageNotReadableException` |
| `SENTENCE_FEEDBACK_NOT_FOUND` | 404 | `SentenceFeedbackNotFoundException` (IDOR 통일 §4) |
| `SENTENCE_FEEDBACK_INVALID_TRANSITION` | 409 | `SentenceFeedbackInvalidTransitionException` |
| `SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION` | 400 | `SentenceFeedbackUnknownSuggestionException` |
| `SENTENCE_FEEDBACK_RATE_LIMITED` | 429 | `SentenceFeedbackRateLimitedException` |
| `INTERNAL_ERROR` | 500 / 401 (auth) | `Exception` fallback / `UnauthorizedException` |

`InvalidSuggestionException` (도메인 502 게이트) 은 chat-service invariant 위반 — Application 에서 이미 fallback FAILED 일원화 (PR #71) → 사용자 응답에 도달 X.

`UnauthorizedException` 매핑 시 ErrorCode 는 `INTERNAL_ERROR` 사용 (sentence-feedback 도메인 enum 에 별도 AUTH 코드 미도입 — 인증 실패는 도메인 무관). 후속 `common-auth-web` 추출 시 별 `AuthErrorCode` 분리 검토.

### 8. ExceptionHandler 격리 — assignableTypes + HIGHEST_PRECEDENCE

identity `ProfileExceptionHandler` 패턴 정합 — 다른 sub-domain controller 가 후속 PR 에 추가될 때 generic advice 와 매핑 충돌 회피. 새 controller 추가 시 `assignableTypes` 명시 추가 필요.

### 9. OpenAPI 의무 충족 — diary-service 첫 controller PR

| 항목 | 결정 |
|---|---|
| 의존성 | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0` |
| OpenApiConfig | `infrastructure/config/OpenApiConfig.java` — title `jamo diary-service API` + BearerJwt scheme |
| application.yaml | `springdoc.swagger-ui.path=/swagger` + `api-docs.path=/v3/api-docs` |
| prod profile | multi-document override — `swagger-ui.enabled=false` + `api-docs.enabled=false` |
| `@SecurityRequirement` | controller 클래스 레벨 `BearerJwt` (3 endpoint 모두 인증 필수) |

CLAUDE.md "새 서비스 OpenAPI 의무" 정합 (identity-service 패턴).

### 9-a. JWT verify Bean 그래프 + cross-service Redis blacklist (security-reviewer C1)

| 컴포넌트 | 위치 | 역할 |
|---|---|---|
| `JwtVerifierProperties` (record) | `infrastructure/config/` | `jamo.jwt.{issuer,audience,keyId,publicKeyPem,clockSkew}` — verify-only (privateKey/TTL X) |
| `JwkPemReader` (verify-only 슬림) | `infrastructure/config/` | `readVerificationKey(publicKeyPem, keyId)` — RSA public key 만 파싱 |
| `KeyProvider` Bean | `DiaryServiceConfig.jwtVerifierKeyProvider` | `RsaKeyPairKeyProvider` |
| `JwtVerifier` Bean | `DiaryServiceConfig.jwtVerifier` | `RsaJwtVerifier` (sig + exp + issuer + audience + sid blacklist) |
| `BlacklistChecker` Bean | `SessionBlacklistRedisChecker` `@Component` | identity-service `SessionBlacklistRedisStore` 와 같은 키 schema (`bl:sid:{sid}`) read-only EXISTS |

**cross-service Redis 키 schema 공유**: identity-service `SessionBlacklistRedisStore` (logout / refresh 회전 시 SET) 와 diary-service `SessionBlacklistRedisChecker` (verify hot path 에서 EXISTS) 가 같은 prefix `bl:sid:` 공유. 키 schema 변경 시 양 서비스 동시 변경 필수 — 후속 `common-infrastructure` 모듈로 키 schema 상수 추출 검토 박제.

근거 (security-reviewer C1):
- 부팅 차단 회피 — `LoginUserArgumentResolver` 가 `JwtVerifier` 의존하는데 Bean 그래프 없으면 ApplicationContext 시작 실패.
- sid blacklist 미적용 시 logout 후 access TTL (15분) 윈도우 내 token 재사용 가능 → cross-service Redis 공유로 즉시 거부.

### 9-b. Bearer 토큰 cause 누출 차단 (code-reviewer H2)

`LoginUserArgumentResolver` 의 `JwtVerificationException` catch 시 `cause` **미전파** — cause stack 의 ParseException 등에 토큰 원문 누출 가능 (OWASP A09 Logging Failures). exception class name 만 server-side log:

```java
catch (JwtVerificationException e) {
    throw new UnauthorizedException(
        "access token verification failed: " + e.getClass().getSimpleName());  // cause 제거
}
```

### 10. AuthenticatedUserNotFoundException 부재 결정

identity profile 은 `AuthenticatedUserNotFoundException` (인증된 토큰의 userId 가 DB 부재 = 시스템 invariant 위반) 을 명시 매핑. sentence-feedback 은 본 예외 미도입 — 사용자별 sentence_feedback row 가 없는 것은 정상 흐름 (첫 사용자) 이고, accept / reject 의 `findByIdAndUserId` 가 empty → 404 IDOR 통일 (§4 정합).

후속: 회원 탈퇴 Saga (`UserWithdrawalRequested`) 가 user_data_purged 회신 발행 시점에 sentence-feedback 도 cascade 됨 (PR #71 박제 §14) — invariant 위반은 발생하지 않음.

## 검토한 옵션 (요약)

### Option A. `common-auth-web` 모듈 추출 — 거부 (premature)
본 PR 스코프 폭증 + 두 번째 controller 도입 시점에 일괄 추출이 경제적.

### Option B. XSS escape 서버 책임 (HTML escape) — 거부
JSON spec 위반 + 클라가 다시 unescape 필요. 일반 REST API 패턴 X.

### Option C. Reject 200 + Result body 응답 — 거부 (PRD KEEP)
PRD §2 가 204 명시. 클라이언트가 추가 정보 필요하면 후속 GET endpoint 도입 (현재 Non-Goals).

### Option D. Bean Validation 미적용 (도메인 VO 만 의존) — 거부
1차 빠른 거부 / OpenAPI 자동 schema 생성 / 다양한 invariant 표현 — Bean Validation 가치 큼.

## 결과 및 영향

### 즉시
- `decisions/_index.md` 1행 추가.
- diary-service 가 controller 첫 도입 PR — OpenAPI 의무 충족 (identity 정합).
- 첫 Redis 의존 도입 (sentence-feedback rate limit). 다른 sub-domain (validation / comment 등) 도 동일 패턴 활용.
- presentation/web 5 컴포넌트는 후속 sub-domain controller 가 그대로 재사용.

### 후속 PR 시리즈

```
D-a-5-impl-batch                : EXPIRED 전이 배치 (Quartz / Spring Batch) + 90일 보존 cleanup
(별 PR) common-auth-web         : LoginUser/AuthenticatedUser/Resolver/UnauthorizedException 공통 모듈 추출
                                   (두 번째 도메인 controller 등장 시점 — chat / learning / platform / 또는 diary 의 다른 sub-domain)
(별 PR) Spring Security         : CSRF / CSP / nosniff / referrer-policy 헤더 + 인증 필터 표준화
(별 PR) E2E (Testcontainers)    : 3 endpoint 풀 시퀀스 + Redis rate limit 실 동작 + 401 / 403 / 429 verify
```

### 결정 대기 (본 결정에서 다루지 않음)

- Rate limit 정확 값 운영 데이터 후 조정 (분 10 / 일 50).
- TOCTOU race 의 atomic Lua / 분산 lock 도입 — 운영 측정 후.
- presentation/web 5 컴포넌트 → common-auth-web 모듈 추출 시점.
- OpenAPI 자동 schema 의 Bean Validation 메시지 i18n.

### Non-Goals
- Spring Security 통합.
- E2E (Testcontainers) — 별 PR.
- common-auth-web 모듈 추출.
- HTML escape 서버 책임 (클라 책임).
- Reject endpoint 의 200 + body 응답.

## 참고

- [`decisions/diary/sentence-feedback-domain-policy.md`](sentence-feedback-domain-policy.md) — 권한 가드 / 응답 schema / quota
- [`decisions/diary/sentence-feedback-infra-decisions.md`](sentence-feedback-infra-decisions.md) — sanitization / Resilience4j
- identity-service `ProfileController` (PR #46) — 참고 패턴
- [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md)
- [ADR-0005 외래 ID + JPA 연관관계 금지](../../adr/0005-no-jpa-associations.md)
- [CLAUDE.md](../../../CLAUDE.md) — "새 서비스 OpenAPI 문서화" 의무
