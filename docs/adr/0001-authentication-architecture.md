# ADR-0001: 인증 아키텍처

- **상태**: Accepted
- **결정일**: 2026-04-26
- **결정자**: jonghun

## 컨텍스트
jamo 백엔드는 13개 도메인 / ~30 API의 멀티모듈 MSA. PRD 분석 결과 OAuth2 소셜 로그인 3개(KAKAO/NAVER/GOOGLE) + LOCAL(이메일/비밀번호) 가입 + 이메일 검증 흐름 + Refresh Token Rotation + Logout 시 토큰 무효화 요구가 식별됨. 클라이언트는 자체 SPA/모바일에 한정되며, 외부 third-party 공개 계획·MFA 요구·Spring Cloud 운영 경험·인프라 단 Gateway 모두 부재. 디바이스별 강제 로그아웃("다른 기기에서 로그아웃") 요구 있음.

## 검토한 옵션

### Option A: Spring Cloud Gateway + Spring Authorization Server (full-stack)
- **장점**: 표준 OIDC, 외부 OIDC 공개 가능, 단일 진입점에서 rate limit/CORS/사전 토큰 검증
- **단점**: 학습곡선 매우 높음, 2개 컴포넌트 추가 운영, 30 API 규모 대비 ROI 낮음, Gateway·AuthServer 각각 SPOF
- **적합성**: 외부 OIDC 공개·MFA 요구가 부재해 채택 근거 약함

### Option B: 별도 auth-service (자체 JWT 발급) + 게이트웨이 없음 + 각 서비스 JWT 직접 검증
- **장점**: 단순, PRD 흐름과 자연스럽게 매칭, JWT 검증을 공통 라이브러리화 가능, stateless 수평확장
- **단점**: L7 라우팅·rate limit은 인프라 단(ALB/Nginx) 별도 처리 필요, 즉시 무효화는 Redis 블랙리스트로 보강, JWKS 키 회전 직접 운영
- **적합성**: 외부 공개 없음 + SPA/모바일 + 13 도메인 멀티모듈에 가장 정합

### Option C: User 서비스가 인증 책임 통합
- **장점**: 모듈 1개 절감, LOCAL 가입 후 자동 로그인이 한 트랜잭션
- **단점**: User Aggregate + AuthCredential + RefreshToken + EmailVerification + 3 IdP 연동이 한 모듈에 섞여 응집도 깨짐, Bounded Context 정합성 낮음
- **적합성**: 비추 — auth 흐름 복잡도(3 IdP + LOCAL + 토큰 회전 + 블랙리스트)와 충돌

### Option D: auth-service + Spring Cloud Gateway (Authorization Server 제외)
- **장점**: 단일 진입점에서 rate limit/CORS/사전 검증/WAF 후크
- **단점**: Gateway 운영 부담 추가, 30 API 규모에선 ALB/Nginx로 대체 가능, Gateway SPOF
- **적합성**: 외부 공개·단일 진입점 요구가 명확할 때만

## 결정

**Option B 채택.** 이유:
1. 외부 공개·MFA·Spring Cloud 운영 인력·인프라 Gateway 모두 부재 → A/D의 추가 비용 정당화 불가
2. PRD의 BFF + Authorization Code + JWT(access+refresh) 흐름이 자체 auth-service 모델과 자연스럽게 매칭
3. LOCAL→소셜 only 전환 가능성이 있으므로 user에서 auth 책임을 분리해 변경 영향을 격리

### 세부 정책

| 항목 | 정책 |
|---|---|
| Access Token | JWT (RS256), TTL 15분, claim: `sub(userId)`, `sid(sessionId UUID)`, `device(deviceId)`, `iat`, `exp` |
| Refresh Token | JWT 회전형, TTL 30일, Redis SoT `refresh:{userId}:{sessionId}` 에 `{deviceId, hash, issuedAt}` 저장 |
| 검증 | 각 서비스의 JWT 필터가 (a) signature/exp 검증 (b) Redis blacklist `bl:sid:{sid}` 존재 시 거부 |
| 단일 디바이스 로그아웃 | 해당 `sid` blacklist 등록 + refresh 키 삭제 |
| 전 디바이스 로그아웃 | `refresh:{userId}:*` 일괄 폐기 + 모든 sid blacklist 등록 |
| OAuth | Authorization Code Grant + **PKCE 필수**. 서버측 일회성 authCode (Redis 60s TTL) → SPA가 `exchange` 로 토큰 수령 (BFF 패턴 유지) |
| 키 회전 | auth-service 가 JWKS endpoint `/.well-known/jwks.json` 노출. 다른 서비스는 캐싱 후 주기 갱신 |
| 검증 라이브러리 | 공통 모듈 `common-auth-jwt` 로 표준화 |
| 토큰 전파 | gRPC metadata `authorization: Bearer <accessJWT>` 그대로 Token Relay. 서비스간 별도 토큰 발급 안 함. |
| 모듈 매핑 | auth와 user는 동일 MySQL 스키마 → 동일 서비스 모듈 안의 다른 도메인 패키지로 배치 (별도 배포 분리 여부는 ADR-0002에서 확정) |

## 결과 및 영향

### 긍정적
- 운영 단순함 (Gateway/AuthServer 부재 = SPOF 감소, 학습 비용 낮음)
- stateless 검증으로 서비스 수평 확장 자유
- LOCAL 제거 시 변경 범위가 auth 모듈에 한정
- gRPC Token Relay로 권한 컨텍스트 전파가 단순

### 부정적 / 트레이드오프
- 외부 OIDC 공개가 필요해지면 auth-service 위에 Spring Authorization Server 마이그레이션 필요
- L7 라우팅·rate limit·CORS는 인프라(ALB/Nginx) 또는 서비스별 처리 필요
- Redis 장애 시 blacklist 검증 불가 → fail-closed 정책 별도 결정 필요
- JWT 키 회전(JWKS) 운영 부담

### 후속 결정이 필요한 항목
- [ ] LOCAL 가입 후 자동 로그인 여부
- [ ] PKCE 미지원 클라이언트 fallback 정책
- [ ] Redis 장애 시 fail-open vs fail-closed
- [ ] state 쿠키 SameSite 속성 (모바일 WebView/SPA 도메인 구성에 따라)
- [ ] JWKS 캐싱 TTL과 키 회전 주기
- [ ] 서비스간 mTLS 도입 시점
- [ ] auth/user/profile 모듈 분리 여부 → **ADR-0002에서 결정**

## 참고
- 관련 ADR: ADR-0002 (서비스 분할 — auth/user 모듈 매핑 확정)
- PRD: `docs/prd/auth/{start,callback,exchange,refresh,logout}.md`, `docs/prd/user/{createUser,sendValidationNumber,validateEmail,getMyInfo}.md`
- 외부 표준: RFC 6749 (OAuth 2.0), RFC 7636 (PKCE), RFC 7519 (JWT), RFC 7517 (JWKS)
