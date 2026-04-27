# jamo-backend 문서 인덱스

jamo-backend 프로젝트의 모든 문서 진입점. 코드만 봐서는 알 수 없는 **의사결정의 흔적·요구사항·아키텍처 매핑**을 한 곳에 모은다.

## 문서 카테고리

| 카테고리 | 위치 | 목적 |
|---|---|---|
| **ADR** (Architecture Decision Record) | [`adr/`](adr/) | 되돌리기 어려운 아키텍처/도메인 의사결정. 옵션·트레이드오프·근거 포함 |
| **PRD** (Product Requirements Document) | [`prd/`](prd/) | 도메인별 API 단위 요구사항 명세. 13개 도메인 / 60+ API |
| **Architecture** | [`architecture/`](architecture/) | 서비스/도메인 매핑, contracts 카탈로그, 다이어그램 |
| **Decisions** (도메인 단위) | [`decisions/`](decisions/) | 작은 결정·정책 기록 (ADR 보다 가벼운 단위) |

> 문서화 정책: 2개 이상의 옵션을 비교했거나, 코드만 봐서는 의도를 알 수 없는 모든 결정은 **ADR 또는 Decision Log 로 자율 작성** 후 본 README 와 카테고리 별 `_index.md` 에 등록한다. (참고: `.claude/skills/documentation/`)

---

## 빠르게 찾기

### 처음 온 사람용

1. **시스템 전체 그림**: [`architecture/service-domain-mapping.md`](architecture/service-domain-mapping.md) — 5 Java + 1 Python 서비스의 도메인/통신 매핑
2. **왜 이런 구조인가**: [ADR-0002 서비스 분할](adr/0002-service-decomposition.md), [ADR-0003 AI 호출 분리](adr/0003-ai-call-architecture.md)
3. **인증은 어떻게 작동하는가**: [ADR-0001 인증 아키텍처](adr/0001-authentication-architecture.md), [ADR-0006 OAuth Provider 통합](adr/0006-oauth-provider-integration.md)
4. **DB/JPA 작성 시**: [ADR-0005 JPA 연관관계 금지](adr/0005-no-jpa-associations.md) **— 모든 새 엔티티에 적용**

### 주제별 탐색

| 주제 | 핵심 문서 |
|---|---|
| 인증/OAuth | [ADR-0001](adr/0001-authentication-architecture.md), [ADR-0006](adr/0006-oauth-provider-integration.md), [`prd/auth/`](prd/auth/) |
| 서비스 경계 / 통신 | [ADR-0002](adr/0002-service-decomposition.md), [ADR-0004 contracts 명명·버전](adr/0004-contracts-naming-and-versioning.md), [`architecture/contracts-catalog.md`](architecture/contracts-catalog.md) |
| AI / Python ai-service | [ADR-0003](adr/0003-ai-call-architecture.md) |
| 데이터 모델링 정책 | [ADR-0005](adr/0005-no-jpa-associations.md) |
| 도메인별 API 명세 | [`prd/_index.md`](prd/_index.md), [`prd/_status.md`](prd/_status.md) |

---

## 인덱스 파일

각 카테고리에는 자체 인덱스가 있다. 새 문서 추가 시 카테고리 인덱스도 함께 갱신:

- [`adr/_index.md`](adr/_index.md) — ADR 목록 (상태/주제별)
- [`prd/_index.md`](prd/_index.md) — PRD 도메인 목록
- [`prd/_status.md`](prd/_status.md) — PRD 진행 트래커 (KEEP/DROP/FIX)
- [`decisions/_index.md`](decisions/_index.md) — 도메인 단위 결정 로그

---

## 참고

- 프로젝트 루트 규칙: [`/CLAUDE.md`](../CLAUDE.md)
- 작업 절차 / 스킬: `.claude/skills/`
- 리뷰 기준 / 에이전트: `.claude/agents/`
