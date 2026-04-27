# Decision: ConfigurationProperties record 의 패키지 위치

- **상태**: Accepted (단기 우회로 명시)
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **PR**: PR3-b (`feature/identity-auth-oauth-flow`)
- **관련**: code-reviewer H1 지적, CLAUDE.md 핵심 원칙 #2

## 컨텍스트

PR3-b 구현 중 code-reviewer 가 지적: **Application 계층의 service 들이 `infrastructure/config/` 의 `JwtProperties`, `OAuthProviderProperties`, `RefreshTokenHashProperties` 를 직접 import** — CLAUDE.md 핵심 원칙 #2 ("의존성은 항상 안쪽으로 — Domain ← Application ← Infrastructure") 에 위배되는 의존 방향.

구체 사용처:
- `OAuthStartService` → `OAuthProviderProperties` (provider 별 설정 + state-cookie max-age)
- `OAuthCallbackService` → `OAuthProviderProperties` (authcode TTL)
- `AuthExchangeService` → `JwtProperties` (access/refresh TTL)

이 record 들은 **`@ConfigurationProperties` 어노테이션만 Spring 이고 record 자체는 framework-free** — 의미상 application 정책에 가깝다.

## 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. application 에 Settings 추상화 (record + 어댑터)** 도입 | 의존 방향 정확. 미래 설정 소스 변경 (yaml → ConfigServer 등) 시 application 안정 | 신규 record 6+ 개. Infrastructure 가 어댑터 변환 코드. PR3-b 의 핵심 흐름 작업과 무관한 보일러플레이트 추가 |
| **B. Properties record 들을 `application/config/` 로 이동** | record 위치만 옮김 (1줄 변경 × 3 파일). Infrastructure 가 이 record 를 사용하는 코드 (`AuthorizationCodeRedisStore` 등) 도 application 패키지를 import — 정상 (Infrastructure → Application 의존) | CLAUDE.md 디렉토리 구조의 "infrastructure/config = Spring 설정" 가이드와 자그마한 충돌 |
| **C. 단기 우회 — 현 위치 유지 + 본 결정 문서화** | 즉시 머지 가능. 본 PR 이 OAuth 흐름 자체에 집중 | 의존 방향 위반 잔존. ArchUnit 룰로 잡지 못함 |

## 결정 — **C 채택 (단기 우회)** + B 후속 검토

**현 시점에는 Properties 위치를 그대로 두고**, 본 결정을 docs 로 박제. PR4 또는 별도 정리 PR 에서 옵션 B (또는 A) 로 정리.

## 근거

1. **변경 비용 vs 가치 비교**: 옵션 A 는 Settings 추상화 신규 record 와 어댑터 코드가 늘어나 PR3-b 의 OAuth 흐름 코드 변경과 섞인다. PR 사이즈/리뷰 부담이 커지고, 변경 사유가 두 가지로 혼재 (원자적 PR 원칙 위배).
2. **의존 방향 위반의 실제 위험**: Properties record 들은 framework-free (`@ConfigurationProperties` 외 import 없음). Application 이 이 record 의 정확히 어떤 필드를 쓰는지는 명확하고, Infrastructure 의 다른 변경이 application 에 파급할 위험은 작다.
3. **자율 문서화 정책**: `feedback_decision_documentation` 메모리에 따라 의도된 단기 우회는 docs 로 명시 — 미래의 자기/다른 개발자가 "왜 이렇게 됐지?" 의문을 갖지 않도록.

## 결과 및 영향

### 즉시
- PR3-b 의 application service 들이 `app.backend.jamo.identity.infrastructure.config.{JwtProperties, OAuthProviderProperties}` 를 직접 import 한 채로 머지.
- ArchUnit 의 "domain → 외곽 계층 차단" R3-b 룰 (PR3-a 도입) 은 application 계층까지는 검사하지 않음 — 그러므로 본 의존 위반은 자동 검증되지 않는다 (수동 인지 필요).

### 후속 (PR4 또는 별도 정리 PR)

다음 중 하나 선택:

#### B. Properties 패키지 이동 (가벼운 정리)
```
infrastructure/config/{Jwt,OAuthProvider,RefreshTokenHash}Properties.java
    →  application/config/{...}.java
```
- 모든 사용처 (Application + Infrastructure 양쪽) 의 import 경로 갱신.
- Spring `@ConfigurationProperties` 는 패키지 위치 무관하게 작동.
- ArchUnit 에 룰 추가:
  ```java
  // application 은 infrastructure 를 import 하지 않는다 (단, contracts/common-* 제외)
  noClasses().that().resideInAPackage("...application..")
      .should().dependOnClassesThat().resideInAPackage("...infrastructure..")
  ```

#### A. Settings 추상화 (강한 정리, 더 큰 비용)
- `application/config/JwtSettings`, `AuthFlowSettings`, `OAuthProviderSettings` record 신설.
- Infrastructure 의 Properties 가 어댑터 메서드로 변환 + Bean 등록.
- Application 은 Settings 만 의존 — 미래 설정 소스 변경 (ConfigServer, Vault 등) 시 application 코드 무영향.

### 추가 권장

- ArchUnit 에 application → infrastructure 차단 룰을 도입할 때, `application/config` 또는 어댑터 위치를 예외로 허용하는 식으로 fine-tune.
- 같은 패턴 (application 이 infrastructure 패키지의 record/exception 등을 import) 이 다른 모듈(`diary-service`, `chat-service` 등)에 도입되기 전 정리 권장.

## 후속 결정 항목

- [ ] **PR4 또는 별도 정리 PR**: 옵션 B 또는 A 채택 + 코드 변경 + ArchUnit 룰 추가.
- [ ] 같은 위반 패턴이 다른 서비스 모듈에 발생하기 전 사전 ArchUnit 룰 추가 가능성 검토 (diary/chat/learning/platform 의 Application Service 가 첫 도입될 때).

## 참고

- CLAUDE.md 핵심 원칙 #2 (의존 방향)
- `.claude/skills/ddd-architecture/SKILL.md` §3 (Application 계층 규칙)
- `.claude/skills/module-boundary/references/archunit-rules.md` R3 (domain framework-free) — application 까지 확장 후보
