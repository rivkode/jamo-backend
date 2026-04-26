# ADR-0005: JPA 연관관계 어노테이션 금지 — ID 참조 + 어플리케이션 검증

- **상태**: Accepted
- **결정일**: 2026-04-27
- **결정자**: jonghun
- **관련 ADR**: [ADR-0002 서비스 분할](0002-service-decomposition.md)

## 컨텍스트

identity-service 의 `User` Aggregate 와 `OAuthIdentity` 매핑 시점에 두 가지 패턴이 가능했다.

- **A. JPA association 사용**: `@OneToMany(mappedBy="user", cascade=ALL)` + `@ManyToOne UserJpaEntity user` + DB FK constraint
- **B. ID 만 참조**: `OAuthIdentityJpaEntity.userId : UUID` 컬럼만 보유, FK constraint 도 정의하지 않음, 존재 검증은 어플리케이션 로직에서

향후 모든 도메인 (diary↔comment, chat↔chatroom, sentence↔word 등) 에서 동일 결정이 반복될 것이므로 표준화 필요.

## 검토한 옵션

### Option A — JPA association (관습)

- **장점**: cascade 자동 처리, 객체 그래프 fetch 한 번에, ORM-friendly 한 ER 다이어그램
- **단점**:
  - LAZY/EAGER 트레이드오프, N+1 문제, `LazyInitializationException` 등 ORM 마법
  - cascade 가 의도치 않게 다른 Aggregate 까지 흘러감 (Aggregate 경계 침범)
  - DB FK constraint 가 데이터 백필/마이그레이션/샤딩 시 부담
  - ON DELETE CASCADE 가 도메인 의도를 가림 — Aggregate Root 의 `delete()` 가 자식 삭제까지 자동 처리됨을 코드에서 안 보임
  - 멀티 DB / Database-per-service 환경에서 cross-schema FK 가 사실상 불가
- **적합성**: ORM 학습 비용은 크지만 단일 schema 모놀리식 CRUD 에는 자연스러움

### Option B — ID 참조 + 어플리케이션 검증 (채택)

- **장점**:
  - Aggregate 경계가 코드에 명시적 (Vernon, *IDDD* §10 "Reference by Identity")
  - cascade 가 명시적 — 회원 탈퇴 Saga (ADR-0002 §7) 같이 cross-aggregate / cross-service 정리가 도메인 이벤트 + 명시 삭제로 표현됨
  - DB 마이그레이션 / 샤딩 / Database-per-service (ADR-0002) 와 정합
  - ORM 마법 (LAZY proxy, dirty checking 의 cascade) 으로 인한 디버깅 시간 감소
  - 테스트 시 객체 그래프가 단순 (작은 객체로 자유 합성 가능)
- **단점**:
  - 매 조회 시 두 번 쿼리 (User + OAuthIdentities) — 관측 가능성 ↑, 의도적 비용
  - cascade 자동 안 됨 → Repository 구현체 또는 Application Service 가 직접 처리
  - 무결성 검증을 어플리케이션 로직에 위임 — 동시성 race 가 있으면 dangling FK 가능 (트랜잭션 + 유니크 인덱스 + 도메인 invariant 로 방어)
- **적합성**: MSA + DDD + 멀티 schema 학습 케이스에 정합

## 결정

**Option B 채택.**

### 규칙

1. **JPA 연관관계 어노테이션 금지** — `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany` 모두 사용하지 않는다.
2. **FK 컬럼은 외래 ID 만 보유** — 예: `OAuthIdentityJpaEntity.userId : UUID` (`@Column(name = "user_id")`).
3. **DB 레벨 FK constraint (`FOREIGN KEY ... REFERENCES`) 도 정의하지 않는다** — `ON DELETE CASCADE` 같은 자동 정리도 금지.
4. **존재 검증은 어플리케이션 로직** — Application Service 또는 Domain Service 가 `userRepository.findById(userId)` 로 부모 존재 확인 후 자식 생성/수정. 검증 누락은 코드 리뷰에서 잡는다.
5. **인덱스는 명시적으로 추가** — `INDEX idx_<table>_<column> (<column>)` 로 조회 성능 보장. FK constraint 가 없으니 자동 인덱스도 없다.
6. **cascade 는 명시적 처리** — 부모 삭제 시 자식 삭제는 Repository 구현체에 명시 (`oauthIdentityRepository.deleteAllByUserId(userId)`) 또는 도메인 이벤트 + 명시 삭제.
7. **ArchUnit R10 으로 강제** — 모든 Java 서비스의 `ArchitectureTest` 에 JPA association 어노테이션 사용 금지 규칙을 추가한다.

### 예시 (identity-service)

```java
// ❌ 금지
@Entity
public class OAuthIdentityJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpaEntity user;
}

// ✅ 권장
@Entity
public class OAuthIdentityJpaEntity {
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;
}
```

```sql
-- ❌ 금지
CREATE TABLE oauth_identity (
    ...,
    CONSTRAINT fk_oauth_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ✅ 권장
CREATE TABLE oauth_identity (
    ...,
    INDEX idx_oauth_identity_user_id (user_id)
);
```

### Aggregate Root 의 자식 영속화

`UserRepository.save(User)` 같은 Aggregate Root 단위 저장은 내부적으로:
1. `UserJpaEntity` 저장 (`SpringDataUserRepository`)
2. 새 `OAuthIdentityJpaEntity` 들 저장 (`SpringDataOAuthIdentityRepository.saveAll(...)`)
3. (선택) 삭제된 자식이 있으면 명시 삭제

같은 트랜잭션 (`@Transactional` 은 Application Service) 안에서 처리. cascade 마법 없이 명시적.

## 결과 및 영향

### 긍정적
- Aggregate 경계가 코드에 그대로 드러남
- DB 마이그레이션 / 샤딩 / 서비스 분리 시 schema 변경 자유도 ↑
- ORM N+1 / `LazyInitializationException` / 의도치 않은 cascade 없음
- 회원 탈퇴 Saga (ADR-0002 §7) 의 cross-service 정리가 자연스럽게 매핑

### 부정적 / 트레이드오프
- 부모-자식 fetch 가 두 쿼리 (성능 비용 — 의도된 비용)
- 자식 영속화 로직을 Repository 구현체가 직접 작성 (ORM cascade 없음)
- 어플리케이션 검증 누락 시 dangling FK 발생 가능 → 트랜잭션 + 유니크 인덱스 + 코드 리뷰로 방어

### 후속 결정이 필요한 항목
- 부모 삭제 시 자식 정리 패턴 통일 (Repository 메서드 vs 도메인 이벤트 + Listener)
- 회원 탈퇴 Saga 에서 cross-service 정리 메시지 형식
- ArchUnit R10 의 정확한 시그니처 (각 서비스 ArchitectureTest 에 동일 규칙 복제)

## 참고

- Vaughn Vernon, *Implementing Domain-Driven Design*, 10장 "Aggregates" — Reference Other Aggregates by Identity
- Eric Evans, *Domain-Driven Design*, "Aggregates"
- [Java 도메인 객체와 JPA Entity 분리 — Aggregate by ID](https://martinfowler.com/articles/microservices.html) (Martin Fowler — Database per Service / Bounded Context)
- 관련 ADR: [ADR-0002](0002-service-decomposition.md) (Database per Service)
