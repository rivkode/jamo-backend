# ADR Index

Architecture Decision Records — jamo-backend 의 되돌리기 어려운 아키텍처/도메인 의사결정 모음. 작성 정책은 [`docs/README.md`](../README.md) 참조.

## 작성 가이드

- 파일명: `NNNN-{kebab-slug}.md` (4자리 일련번호)
- 구조: `상태 / 컨텍스트 / 검토한 옵션 / 결정 / 결과 및 영향 / 후속 결정 / 참고`
- **반드시 옵션 2개 이상을 비교하고 트레이드오프를 적는다**. 단일 옵션이면 ADR 가치가 없다.
- 머지 후 본 인덱스에 한 줄 요약 추가.
- 변경 시: 새 ADR 작성으로 supersede (기존 ADR 의 상태를 `Superseded by ADR-NNNN` 으로 변경, 본문은 보존).

## 상태 정의

| 상태 | 의미 |
|---|---|
| `Proposed` | 검토 중, 아직 채택 전 |
| `Accepted` | 채택. 현재 유효 |
| `Superseded by ADR-NNNN` | 새 ADR 로 대체됨 |
| `Deprecated` | 더 이상 적용 안 됨, 대체 ADR 도 없음 |

## 인덱스

| ID | 제목 | 상태 | 결정일 | 한 줄 요약 |
|---:|---|---|---|---|
| [0001](0001-authentication-architecture.md) | 인증 아키텍처 | Accepted | 2026-04-26 | OAuth2 + LOCAL + RS256 JWT(access+refresh) + Redis blacklist. Gateway/AuthServer 미도입 |
| [0002](0002-service-decomposition.md) | 서비스 분할 | Accepted | — | 13개 도메인 → 5 Java + 1 Python 서비스 매핑. `auth/user/profile` → identity-service |
| [0003](0003-ai-call-architecture.md) | AI 호출 아키텍처 | Accepted | — | chat-service 가 ai-service(Python, gRPC) 의 단일 진입점. 다른 서비스 직접 호출 금지 |
| [0004](0004-contracts-naming-and-versioning.md) | contracts 명명·버전 | Accepted | — | proto/event 의 패키지·field number·breaking change 처리 규칙 |
| [0005](0005-no-jpa-associations.md) | JPA 연관관계 금지 | Accepted | 2026-04-26 | `@ManyToOne`/`@OneToMany` 등 금지. 외래 ID 컬럼만. DB FK 제약도 미사용 |
| [0006](0006-oauth-provider-integration.md) | OAuth Provider 통합 | Accepted | 2026-04-27 | Per-provider PKCE flag, deviceId 헤더, 이메일 중복 시 새 user 등록 (자동 링크 X), per-provider extractor 전략 |

## 주제별 빠른 인덱스

| 주제 | 관련 ADR |
|---|---|
| 인증 / 토큰 / OAuth | 0001, 0006 |
| 서비스 경계 / 통신 | 0002, 0003, 0004 |
| 데이터 모델링 / 영속성 | 0005 |
| 외부 시스템 통합 | 0001, 0003, 0006 |
