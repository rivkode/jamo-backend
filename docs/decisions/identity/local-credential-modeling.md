# Decision: LOCAL 자격증명 모델링 — `users.account_type` + `users.password_hash`

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **PR**: `feature/identity-user-create-local-domain` (PR6-a)
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md), [ADR-0006 OAuth Provider 통합](../../adr/0006-oauth-provider-integration.md)
- **관련 PRD**: [`prd/user/createUser.md`](../../prd/user/createUser.md) §9 FIX

## 컨텍스트

PRD `user/createUser.md` §9 FIX 항목은 LOCAL 회원가입의 이메일 중복 검증을 *"LOCAL 가입 한정으로 어플리케이션 레이어에서 `existsByProviderAndEmail(LOCAL, email)` 로 검증"* 으로 명시했다. 이 의도(intent)를 도메인/DB 모델로 어떻게 구현할지가 후속 결정으로 남았다.

PR6 착수 직전, 두 가지 구현 옵션이 실질적이었다.

## 검토한 옵션

| 옵션 | 구조 | 장점 | 단점 |
|---|---|---|---|
| **A1**. `OAuthIdentity` 테이블 재사용 — `OAuthProvider` enum 에 `LOCAL` 추가, `oauth_identities` row 의 `provider=LOCAL`, `provider_user_id=user_id`, password_hash 는 `users` 테이블 컬럼 | "provider+providerUserId 가 SoT" 라는 ADR-0006 결정 4 의 일관성 | (a) `OAuthProvider.fromExternal` 의 `LOCAL` 거부 의미와 충돌 (b) OAuth 와 LOCAL 의 의미가 한 enum 에 섞여 가독성 저하 (c) `provider_user_id` 의 의미를 LOCAL 케이스에서만 다르게 해석해야 함 |
| **A2**. `users.account_type` (LOCAL/OAUTH) + `users.password_hash` (nullable) 컬럼 추가, OAuthIdentity 는 OAuth 한정 유지 | (a) `OAuthProvider` 의 OAuth 한정 의미 보존 (b) `existsLocalAccountByEmail = WHERE email=? AND account_type='LOCAL'` 명시적 (c) 향후 LOCAL+OAuth 하이브리드 도입 시도메인 invariant 만 완화하면 됨 | DB 컬럼 2개 추가 (Flyway V3) |

## 결정 — 옵션 **A2**

`users` 테이블에 `account_type ENUM('LOCAL','OAUTH') NOT NULL` 과 `password_hash VARCHAR(72) NULL` 컬럼을 추가한다. `OAuthIdentity` / `OAuthProvider` 는 OAuth 전용 의미를 유지.

도메인:
- `AccountType` enum (LOCAL, OAUTH) — `app.backend.jamo.identity.domain.model.user`
- `HashedPassword` VO — record + blank rejection + `toString` 마스킹
- `PasswordEncoder` port — `encode` + `matches`. Infrastructure 어댑터에서 BCrypt cost=12
- `User` aggregate 에 `accountType`, `hashedPassword` (nullable) 필드 + `User.registerLocal(displayName, email, hashedPassword, now)` 정적 팩토리

invariant:
- LOCAL → `hashedPassword != null`, `oauthIdentities.isEmpty()`
- OAUTH → `hashedPassword == null`, `oauthIdentities.size() >= 1`

## 근거

1. **`OAuthProvider` 의 OAuth 한정 의미 보존** — ADR-0006 결정 4 에서 "provider+providerUserId 가 SoT" 라고 한 것은 *OAuth provider* 한정의 기술적 결정. LOCAL 은 OAuth provider 가 아니라 자체 가입 경로이므로 enum 에 섞으면 ADR-0006 의 도메인 의미가 흐려진다. 실제로 `OAuthProvider.fromExternal("LOCAL")` 은 명시적으로 `UnsupportedOAuthProviderException` 을 던지도록 작성돼 있어 enum 추가는 즉시 코드 충돌.
2. **중복 검증 쿼리의 명시성** — `WHERE email=? AND account_type='LOCAL'` 은 코드/SQL 양쪽에서 직관적. A1 의 `WHERE provider='LOCAL'` 도 SQL 자체는 동작하지만 `OAuthProvider` enum 의 의미를 깨고 LOCAL 의 `provider_user_id` 가 user_id 의 중복 저장이 되는 안티패턴 유발.
3. **미래 확장성** — LOCAL+OAuth 하이브리드 (LOCAL 가입 후 OAuth 연결 / OAuth 가입자 비밀번호 설정) 도입 시 A2 는 invariant 만 완화하면 자연스럽게 흡수. A1 은 한 user 에 여러 provider row 를 만들고 그 중 하나가 LOCAL 인 형태가 돼 join 비용 + `LOCAL` 의 의미 모호.
4. **PRD §9 의 표현은 *intent*** — "existsByProviderAndEmail(LOCAL, email)" 의 단어를 그대로 메서드명으로 옮기지 않고 도메인 의미에 맞게 `existsLocalAccountByEmail(email)` 로 표현해도 PRD 의도(=LOCAL 한정 중복 검증) 와 정합. PRD 는 *무엇을* 명세하고 본 결정은 *어떻게* 구현하는지를 다룬다.

## 결과 및 영향

### Domain (PR6-a, 본 PR)
- `AccountType` enum (LOCAL, OAUTH)
- `HashedPassword` VO (`toString` 마스킹)
- `PasswordEncoder` port (encode + matches)
- `User.registerLocal` 정적 팩토리 + `User.restore` 시그니처에 `accountType`, `hashedPassword` 추가
- 도메인 예외 2종: `EmailNotValidatedException` (사전조건 실패), `EmailAlreadyRegisteredException` (LOCAL 중복)
- `UserMapper.toDomain` 임시 매핑 (PR6-b 의 V3 마이그레이션 전까지 OAUTH + null 로 복원) — 기존 모든 사용자가 OAuth 가입자이므로 회귀 안전

### Application + Infrastructure (PR6-b, 후속)
- `RegisterUserCommand/Result/Service` (`@Transactional`, `EmailValidatedFlag.consume` → `existsLocalAccountByEmail` → `passwordEncoder.encode` → `userRepository.save`)
- `BCryptPasswordEncoderAdapter` (Spring Security `BCryptPasswordEncoder` cost=12)
- `spring-security-crypto` 의존성 추가 (full Spring Security 미포함)
- Flyway V3 — `account_type ENUM('LOCAL','OAUTH') NOT NULL`, `password_hash VARCHAR(72) NULL`, `INDEX idx_users_email_account_type (email, account_type)` (LOCAL 중복 검색 최적화)
- `UserJpaEntity` 컬럼 매핑 + `UserMapper` 정합화
- `UserRepository.existsLocalAccountByEmail` + `UserRepositoryImpl` + `SpringDataUserRepository` 메서드 추가

### Presentation (PR6-c, 후속)
- `POST /api/v1/users` (`UserRegistrationController`)
- `RegisterUserRequest/Response` DTO
- `UserErrorCode.EMAIL_NOT_VALIDATED` (400), `EMAIL_ALREADY_REGISTERED` (409) + `UserExceptionHandler` 매핑

### Non-Goals
- 자동 로그인 / 토큰 발급 — PRD §9 결정에 따라 본 PR 시리즈 범위 밖. 별도 LOCAL login PRD/PR 에서 다룸.
- LOCAL 가입자 OAuth 연결 / OAuth 가입자 비밀번호 설정 — 향후 PR 에서 invariant 완화 + 별도 use case.
- `users.email` UNIQUE 제약 — ADR-0006 결정 4 에 따라 두지 않는다 (OAuth 가입자 간 email 충돌 허용). 본 결정은 그 정책과 정합.

## 참고

- [ADR-0006 결정 4](../../adr/0006-oauth-provider-integration.md) — OAuth 가입 시 email 자동 링크 거부
- [PRD user/createUser.md §9](../../prd/user/createUser.md) — KEEP+FIX 분류 결정
- [decisions/identity/user-profile-domain-boundary.md](user-profile-domain-boundary.md) — user 도메인의 write-only 책임 격리
