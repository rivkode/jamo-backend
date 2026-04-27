# Decision: Refresh Token Hash 알고리즘

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md)
- **PR**: PR3-b (`feature/identity-auth-oauth-flow`)

## 컨텍스트

ADR-0001 은 refresh JWT 를 Redis 에 보관하되 **hash 만 저장**하기로 결정 (PR2 PR2 deferred 항목). PR3-b 의 `AuthExchangeService` 에서 refresh JWT 발급 시 hash 알고리즘이 필요하다.

목적:
- 토큰 자체는 클라이언트가 보유 (stateless)
- 서버는 hash 만 보관 → Redis dump 가 유출돼도 토큰 자체는 재구성 불가
- 추후 (PR4 refresh rotation) 에서 클라이언트가 제출한 token 의 hash 가 저장된 hash 와 일치하는지로 reuse 검증

## 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. SHA-256 (단순 해시)** | 표준, 빠름, 의존성 없음 | rainbow table 공격 가능 — pepper 없으면 같은 토큰 → 같은 hash. 운영 secret 격리 불가 |
| **B. bcrypt / argon2id** | password hash 표준 — salt 자동, brute-force 저항 | refresh JWT 검증마다 ~100ms — refresh API 의 응답 시간 부풀림. 우리 토큰은 이미 high-entropy(JWT 256bit) 라 bcrypt 의 work factor 가 과잉 |
| **C. HMAC-SHA256 + 서버 pepper** | (a) 빠름 (sub-ms) (b) pepper 가 secret 격리 — Redis dump 만으론 hash → token 역산 불가 (c) 표준 알고리즘 | pepper 운영 (envvar 관리, rotation 정책) 부담 |

## 결정 — **C 채택**

**HMAC-SHA256 + pepper** (서버측 secret) 사용.

```
hash = base64url( HMAC-SHA256( key=pepper, message=refreshJwtString ) )
```

- pepper: 환경변수 `JWT_REFRESH_HASH_PEPPER`, ≥ 32자 (256 bits) 강제. fail-fast.
- 출력 인코딩: base64url no-padding (URL-safe, Redis key value 비교 용이).
- 구현 위치: `infrastructure/security/HmacRefreshTokenHasher` (port `domain/service/RefreshTokenHasher`).

## 근거

- **B 의 work factor 가 과잉**: refresh JWT 는 256-bit 엔트로피 random + RS256 서명이라 brute-force 위협 모델이 다르다. password (낮은 엔트로피 사람 입력) 에 적합한 bcrypt/argon2 의 비용은 refresh 검증 응답 시간을 직접 늘림. 토큰 검증은 매 refresh 마다 발생하므로 ms 비용이 곧 사용자 latency.
- **A 의 rainbow table 위험**: 단순 SHA-256 은 pepper 없이 hash 가 결정적 — Redis dump + (rare 하지만) 미리 계산된 hash 테이블이 결합되면 일부 공격 표면. pepper 1줄 추가로 mitigate 가능한데 안 할 이유 없음.
- **C 는 산업 표준 절충**: API key / refresh token hash 의 표준 패턴 (Stripe, GitHub PAT 등이 유사). pepper 운영 부담은 전체 운영 secret(JWT 서명 키, DB 비밀번호 등)과 동일 수준 — 추가 인지 부하 없음.

## 결과 및 영향

### 도입
- 새 인터페이스 `domain/service/RefreshTokenHasher`
- 구현 `infrastructure/security/HmacRefreshTokenHasher`
- 설정 `infrastructure/config/RefreshTokenHashProperties` — pepper blank 또는 < 32자 시 fail-fast
- application.yaml 의 `jamo.refresh-token-hash.pepper` 가 envvar `JWT_REFRESH_HASH_PEPPER` 매핑

### 운영
- envvar `JWT_REFRESH_HASH_PEPPER` 를 시크릿 매니저(예: AWS Secrets Manager, Vault) 에 등록
- ≥ 32자 random 생성 권장 (`openssl rand -base64 48` 등)
- **Pepper rotation 정책 (후속 결정 필요)**:
  - 단순 교체: 기존 refresh 토큰 모두 무효 (강제 전체 로그아웃) — UX 큼, 보안 incident 시
  - 점진 교체: 다중 pepper (kid 식별) 보유 → 발급 시 최신, 검증 시 모든 pepper 시도 — 본 결정 범위 외, PR4 refresh rotation 구현 시 재검토

### 운영 모니터링 권장
- application 시작 시 `JWT_REFRESH_HASH_PEPPER` 미설정 → 시작 실패 alarm
- pepper 의 길이/엔트로피는 코드가 강제하므로 별도 metric 불필요

### 보안 review 와의 정합
- security-reviewer 가 PR3-a 에서 H1 (provider response leak) 을 지적했듯이 본 hash 결정도 cause leak 정책과 같은 원칙: secret(pepper) 은 **로그 / 예외 메시지 어디에도 출력하지 않는다**. `HmacRefreshTokenHasher` 가 pepper 를 멤버 변수로만 보유, toString 미override, 예외 메시지에 미포함.

## 후속 결정 항목

- [ ] **PR4 refresh rotation 구현 시**: pepper rotation 전략 (multi-pepper kid 또는 강제 전체 로그아웃)
- [ ] **운영 metric**: refresh hash 검증 실패율 (의심 token reuse 추적)
- [ ] **백업 알고리즘**: HMAC-SHA256 가 미래 위협에 약해질 시 HMAC-SHA-512 로 알고리즘 식별자 (kid prefix) 도입 — 현재는 단일

## 참고

- RFC 2104 (HMAC), RFC 6234 (SHA-2)
- OWASP Cheat Sheet: Cryptographic Storage — "API Keys / Tokens" 섹션
- 유사 사례: Stripe API key (HMAC), GitHub Personal Access Token (HMAC + secret pepper)
