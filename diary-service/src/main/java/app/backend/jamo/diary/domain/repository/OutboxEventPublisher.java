package app.backend.jamo.diary.domain.repository;

/**
 * Outbox 패턴 발행 port — DB 트랜잭션 안에서 Outbox row insert 만 수행.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §12 (3 이벤트 모두 Outbox) +
 * decisions/diary/diary-domain-policy.md §11 (DiaryDeleted Outbox) + CLAUDE.md "Outbox 패턴 (DB 트랜잭션 +
 * Kafka 원자성 위반 방지)" 의무.
 *
 * <p><b>흐름</b>:
 * <ol>
 *   <li>Application Service 가 Aggregate persist 와 동일 트랜잭션 안에서 본 메서드 호출</li>
 *   <li>Adapter (Infrastructure) 가 {@code outbox_event} 테이블에 row insert (eventId / payload JSON / 등)</li>
 *   <li>비동기 발행자 (별 컴포넌트) 가 outbox row → Kafka 토픽 발행 후 row 표시</li>
 *   <li>구독자는 {@code ProcessedEvent} 멱등성 처리 (CLAUDE.md Kafka Consumer 의무)</li>
 * </ol>
 *
 * <p><b>입력 타입</b>: {@code Object} — contracts 의 Kafka record (예:
 * {@link app.backend.jamo.contracts.event.diary.SentenceFeedbackRequested}) 를 받음. Adapter 가 record 의
 * 클래스 / payload 를 inspect 해 직렬화. 도메인은 contracts 의존만 가능 (다른 서비스의 record 도 동일).
 *
 * <p>본 PR 시점 diary-service 자체 port — 후속 서비스에서 동일 패턴 등장 시 common-infrastructure 일반화 검토
 * (premature abstraction 회피).
 */
public interface OutboxEventPublisher {

    /**
     * 이벤트를 outbox 테이블에 insert. 호출자 트랜잭션 안에서 동기 실행 — DB commit 시점에 row 확정.
     *
     * @param event contracts 의 Kafka record (compact constructor 검증을 통과한 객체)
     */
    void publish(Object event);
}
