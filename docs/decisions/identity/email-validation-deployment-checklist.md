# Decision: 이메일 검증 흐름 운영 배포 전 체크리스트

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun (security-reviewer 검토 반영)
- **PR**: PR5-b (`feature/identity-user-email-validation-app-infra`)
- **관련 PRD**: [`prd/user/sendValidationNumber.md`](../../prd/user/sendValidationNumber.md), [`prd/user/validateEmail.md`](../../prd/user/validateEmail.md)
- **관련 결정**: [`identity/user-validation-port-split.md`](user-validation-port-split.md)

## 컨텍스트

PR5-b 가 user 도메인 이메일 검증 application + infrastructure 슬라이스를 도입하면서, security review (PR5-b 시점) 가 **개발 환경에서는 OK 이나 운영 배포 전 반드시 해소되어야 할 항목** 5건을 식별했다. 본 결정은 그 항목을 박제해 운영 진입 시점에 체크리스트로 사용한다.

## 결정 — 운영 배포 BLOCK 조건 (5건)

### B1. `LogEmailSender` 운영 차단 강제

- **현 상태**: `@Profile({"local","dev","test","e2e"})` 적용 — 운영(`prod`) profile 에서 미활성화.
- **운영 진입 시 액션**: 운영 SMTP 또는 SES 어댑터를 별도 PR 로 도입 (`@Profile("prod")` 또는 `@ConditionalOnProperty`). Bean 충돌 방지 — 두 어댑터가 동시에 활성화될 수 없도록 profile 분기 명시.
- **검증**: 운영 배포 후 검증코드가 평문 로그(application log / log forwarding 시스템) 에 등장하지 않는지 1회 검사. `LogEmailSender` 클래스 자체가 운영 빌드에 포함되더라도 빈 등록되지 않으면 OK.
- **근거**: A09 Sensitive Data Exposure (검증코드 평문 로그 = OTP 우회 가능).

### B2. 응답 메시지 attempts 카운트 노출 차단

- **현 상태**: `VerifyValidationCodeService` 의 예외 메시지에서 attempts 정보 제거. 서버 로그(`log.warn`)로만 기록.
- **운영 진입 시 액션**: PR5-c presentation 슬라이스 ExceptionHandler 가 `e.getMessage()` 직접 반환 금지. `ErrorCode` enum (예: `VALIDATION_CODE_MISMATCH`, `VALIDATION_CODE_LOCKED`, `VALIDATION_CODE_EXPIRED`) 만 응답 body 에 포함.
- **검증**: PR5-c E2E 테스트가 응답 body 에 attempts 카운트 부재 단언.
- **근거**: A04 Information Disclosure (잔여 시도 횟수 노출 = brute-force 자동화 최적화 가능).

### B3. dailyLimit 운영값 결정

- **현 상태**: default 5 (envvar `USER_VALIDATION_DAILY_LIMIT` override 가능).
- **운영 진입 시 액션**: 운영 모니터링 데이터 (정상 사용자의 평균 발송 횟수) 기반으로 5 유지 또는 강화. botnet 공격 시나리오 시뮬레이션 (`N email × 5회 시도/day`) 으로 brute-force 표면 산정.
- **검증**: 운영 배포 후 1주일 메트릭 수집 후 조정 (아래 B5 참조).
- **근거**: A04 Insecure Design (dailyLimit 10 시 50회/email/day 추측 가능 → 5 로 강화).

### B4. 정책값 envvar override 지원

- **현 상태**: 모든 정책값 (codeTtl / maxAttempts / cooldown / dailyLimit / validatedFlagTtl) envvar override 지원 (`USER_VALIDATION_*`).
- **운영 진입 시 액션**: 운영 환경 deployment manifest (k8s ConfigMap / Helm values) 에 envvar 명시. 운영 사고 시 fast rollout (코드 빌드 없이 정책 강화) 가능.
- **검증**: 운영 deploy spec 에 envvar 5종 모두 등록되었는지 확인.
- **근거**: A05 Misconfiguration (정책 변경 시 빌드/재배포 필요 = MTTR 길어짐).

### B5. 모니터링 / 알람 메트릭 추가 (별도 PR)

- **현 상태**: 미구현.
- **운영 진입 시 액션**: 별도 PR 로 micrometer 메트릭 추가:
  - `validation_send_total{result="success|rate_limited|email_send_failed"}`
  - `validation_verify_total{result="success|expired|mismatch|locked"}`
  - `validation_code_locked_total` (5회 잠금 빈도 — 공격 지표)
  - `validation_send_failure_total` (Redis/SMTP 장애 — 시스템 지표)
- **검증**: Grafana 대시보드 + 알람 (lockout 빈도 급증, daily limit 도달률 등).
- **근거**: A04 Insecure Design (DoS via Forced Spam Counter — Redis 장애 시 모든 사용자 잠김 → 즉시 감지 필요).

## 추가 후속 결정 (별도 결정 문서 후보)

- **`email-sender-impl.md`**: SMTP vs SES 선택, retry 정책 위치 (어댑터 내부 Resilience4j 권장), 발송 실패 응답 정책 (enumeration 회피 — 항상 같은 응답).
- **password reset 흐름**: `EmailValidatedFlag` port 재사용 vs 별도 flag 도입 — 본 결정에서 미정.

## 결과 및 영향

### 본 슬라이스 (PR5-b) 처리 완료
- B1, B2, B4 코드 반영 완료.
- B3 default 값 5 로 적용.

### 후속 (PR5-c / 운영 진입 PR)
- B2 ExceptionHandler 매핑 (PR5-c).
- B5 메트릭 / 알람 (별도 PR, 운영 진입 직전).
- 운영 SMTP/SES 어댑터 PR (`email-sender-impl.md` 결정 + 어댑터 + B1 검증).

## 참고

- security review trail (PR5-b 시점, 본 PR 본문에 인용)
- OWASP Top 10:
  - A04 Insecure Design
  - A05 Security Misconfiguration
  - A07 Identification and Authentication Failures
  - A09 Security Logging and Monitoring Failures
- ADR-0001 인증 아키텍처
