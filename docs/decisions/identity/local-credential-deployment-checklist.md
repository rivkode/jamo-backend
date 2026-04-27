# Decision: LOCAL 회원가입 운영 배포 체크리스트 + 보안 trade-off

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **PR**: `feature/identity-user-create-local-app-infra` (PR6-b)
- **관련 ADR**: [ADR-0001 인증 아키텍처](../../adr/0001-authentication-architecture.md), [ADR-0006 OAuth Provider 통합](../../adr/0006-oauth-provider-integration.md)
- **관련 PRD**: [`prd/user/createUser.md`](../../prd/user/createUser.md) §9
- **관련 결정**: [identity/local-credential-modeling.md](local-credential-modeling.md) (모델 결정 — A2 채택)

## 컨텍스트

PR6-b 의 multi-agent review (code / test / security) 에서 **security-reviewer NEEDS CHANGES** 판정과 함께 H 2건이 제기되었다. 운영 배포 전에 결단되어야 할 trade-off 와 fail-safe 항목을 본 문서에 박제.

PR5-b 의 [email-validation-deployment-checklist.md](email-validation-deployment-checklist.md) 와 동일 패턴.

## 결정 1 — V3 마이그레이션의 fail-safe 강화 (security H1 대응)

### 변경
- `account_type` 컬럼: `NOT NULL DEFAULT 'OAUTH'` → backfill 후 `MODIFY ... NOT NULL` 로 **DEFAULT 제거**.
- `account_type` 도메인 제약: `CHECK (account_type IN ('LOCAL','OAUTH'))`.
- LOCAL ↔ password_hash invariant: `CHECK ((LOCAL AND password_hash IS NOT NULL) OR (OAUTH AND password_hash IS NULL))` — 도메인 invariant 를 DB 안전망으로 박제.
- `password_hash` 길이: `VARCHAR(72)` → `VARCHAR(255)` (security review L1, 향후 Argon2id 등 알고리즘 교체 대비).
- `idx_users_email` (V1) 제거: leftmost-prefix 매칭으로 `idx_users_email_account_type (email, account_type)` 가 단일 email lookup 도 커버 → redundant index 제거.

### 근거
- DEFAULT 잔존은 INSERT 누락 시 silent OAUTH fallback 을 허용 → 도메인 invariant 와 DB 상태 어긋남 위험.
- CHECK constraint 는 native query / 마이그레이션 / 데이터 패치 등 도메인 layer 우회 경로의 마지막 안전망.

## 결정 2 — BCrypt encode 를 트랜잭션 외부로 이동 (security H2 대응)

### 변경
- `RegisterUserService.register` 의 `@Transactional` 메서드 전체 → `TransactionTemplate.execute(...)` 의 코드 블록 트랜잭션으로 좁힘.
- 트랜잭션 외부: `EmailValidatedFlag.consume` (Redis), `PasswordEncoder.encode` (BCrypt cost=12, ~300ms CPU).
- 트랜잭션 내부: `userRepository.existsLocalAccountByEmail`, `userRepository.save`.

### 근거
- BCrypt cost=12 는 ~250-400ms 의 CPU 작업. 트랜잭션 안에서 실행 시 DB 커넥션이 그동안 점유 → HikariCP 풀(max 10) 기준 동시 가입 30 RPS 만 되어도 풀 고갈 + cascade 장애 위험.
- Spring 권장: long-running CPU work 는 `@Transactional` 외부.
- `TransactionTemplate` 도입 시 self-invocation AOP 문제 회피, application service 의 트랜잭션 경계 자유 조절 가능.

### 부가 영향
- `IdentityServiceConfig.transactionTemplate` Bean 등록 (`PlatformTransactionManager` 의존).
- `RegisterUserServiceTest` 가 `TransactionTemplate` mock 으로 callback 즉시 실행 stub 사용.

## 결정 3 — `EMAIL_ALREADY_REGISTERED` 응답 분리 = LOCAL enumeration window (security M2 — accepted risk)

### 관찰
PR6-c 의 응답 코드 정책:
- 400 `EMAIL_NOT_VALIDATED` — 검증 flag 없음/만료
- 409 `EMAIL_ALREADY_REGISTERED` — LOCAL 가입자 이메일 중복

이 분리는 검증 코드를 통과한 사용자가 다른 검증된 이메일을 시도할 때 LOCAL 가입자 존재 여부를 enumerate 가능 (CWE-203 Observable Discrepancy).

### 결정 — accepted risk
- **enumeration 비용**: 공격자가 이메일 1개당 검증 코드 발송 1회 + 검증 통과 1회를 거쳐야 1 lookup. 자동화 비용이 높음.
- 응답 코드 통합 (예: 두 케이스를 모두 generic 400 으로) 시 사용자 UX 디그라데이션 (왜 가입이 안 되는지 클라이언트가 안내 못함).
- ADR-0006 결정 4: OAuth 가입자와 LOCAL 가입자의 email 충돌은 허용. OAuth 가입자에게는 `existsLocalAccountByEmail` 이 false 반환 → OAuth 가입자 정보는 enumerate 불가. enumeration window 는 LOCAL 가입자 한정.
- **모니터링**: enumeration 시도 패턴 (동일 IP 가 여러 검증된 이메일에 대해 register 시도) 알람 (별도 운영 PR).
- **후속 강화**: createUser 에 IP 기반 rate limit (`createUser:ip:{ip}` Redis counter) — 별도 PR 백로그.

## 결정 4 — `RegisterUserCommand.rawPassword` 라이프사이클 (security L2)

### 정책
- `RegisterUserCommand` 는 **application 내부 전용 record**. presentation 의 `RegisterUserRequest` 가 진입 직후 변환만 사용 — 외부 직렬화 / 로깅 / collection 보관 금지.
- record `toString` 은 `rawPassword=***` 로 마스킹 (현 구현). Logback parameterized message (`log.info("cmd={}", cmd)`) 안전.
- record accessor `rawPassword()` 는 마스킹되지 않음 — 호출자 책임. 본 PR 시점 호출처는 `RegisterUserService.register` 1곳 + 테스트.
- presentation 의 `RegisterUserRequest` (PR6-c) 는 별도 DTO + Bean Validation. Jackson 으로 외부 직렬화는 Request DTO 만, Command 는 절대 외부 노출 X.

### 후속 검증 항목 (PR6-c)
- `RegisterUserRequest` 의 password 필드에 `@JsonIgnore` (응답 직렬화 방어) 또는 별도 응답 DTO 분리.
- `UserExceptionHandler` 에서 `IllegalArgumentException` 명시 매핑 — `User.restore` invariant 메시지가 generic handler 로 새는 것 차단.

## 결정 5 — `application.yaml` defensive error policy

### 추가
```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
```

### 근거
- 도메인 invariant 예외 (`User.restore`/`Email`/`HashedPassword` 의 `IllegalArgumentException`) 가 controller advice 의 명시 매핑을 우회해 Spring Boot generic error handler 까지 도달했을 때, 내부 메시지가 클라이언트 응답에 노출되지 않도록 명시적 차단.
- 디폴트는 Spring Boot 가 `never` 이지만 운영 환경 설정이 무심코 덮어쓸 가능성에 대비해 yaml 에 명시.

## 운영 배포 BLOCK 조건

| 항목 | 검증 방법 |
|---|---|
| V3 마이그레이션 적용 후 `account_type` DEFAULT 가 제거되었는지 | `SHOW CREATE TABLE users` — DEFAULT 절 부재 확인 |
| LOCAL ↔ password_hash invariant CHECK constraint 동작 | LOCAL 가입자 row 에 password_hash NULL 강제 INSERT 시도 → 거부 |
| BCrypt cost=12 가 운영 부하에 적정 (P95 < 500ms) | createUser endpoint 부하 테스트 후 BCrypt 시간 측정 |
| HikariCP `maximumPoolSize` 가 동시 가입 RPS 의 충분 배수 | 운영 트래픽 기준 sizing 검토 |
| 향후 LOCAL 가입자 enumeration 모니터링 alarm 셋업 | 메트릭: 동일 IP 의 register 4xx 응답률 / 단위 시간 |
| `server.error.include-stacktrace: never` 가 운영 profile 에서도 유지 | application-prod.yaml override 점검 |
| V3 backfill 가정 ("기존 모든 사용자 OAuth") 위반 row 부재 | `SELECT COUNT(*) FROM users WHERE id NOT IN (SELECT DISTINCT user_id FROM oauth_identity);` |
| V3 의 `MODIFY COLUMN ... NOT NULL` 이 운영 row 수에 비해 적정 시간에 끝나는지 (InnoDB COPY 알고리즘) | staging 동일 데이터로 사전 측정. row 수 ≥ 100만 시 maintenance window 또는 `pt-online-schema-change` 검토 |
| V3 backfill 중 동시 INSERT race 차단 | Flyway 단일 connection 이지만 다른 인스턴스의 INSERT 는 별개 — 마이그레이션 적용 동안 트래픽 차단 또는 maintenance window |

## 비-블록 후속 작업

- IP 기반 rate limit (security M1) — 별도 PR
- `password_hash` 길이 변경 마이그레이션 (Argon2id 전환 시) — 별도 PR
- `spring-security-crypto` transitive 버전 ≥ 6.3.4 정책 — gradle dependency 점검 PR
- monitoring alarm 셋업 — 운영 PR

## 참고

- [PRD user/createUser.md §9](../../prd/user/createUser.md)
- [Decision local-credential-modeling.md](local-credential-modeling.md)
- [Decision email-validation-deployment-checklist.md](email-validation-deployment-checklist.md) — PR5-b 동일 패턴
- OWASP — Password Storage Cheat Sheet (BCrypt cost 권고)
- OWASP A04 Insecure Design (트랜잭션 안의 long-running CPU work)
- OWASP A05 Security Misconfiguration (DB constraint)
- CWE-203 Observable Discrepancy (응답 코드 분리 enumeration)
