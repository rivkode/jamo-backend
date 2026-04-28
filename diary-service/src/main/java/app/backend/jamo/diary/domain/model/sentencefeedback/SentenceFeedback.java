package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackUnknownSuggestionException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SentenceFeedback Aggregate Root — 일기 한 문장에 대한 AI 대안 제안 라이프사이클.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md (16 항목).
 *
 * <p><b>라이프사이클 (§2)</b>:
 * <pre>
 *   REQUESTED ─(markSuggested)─▶ SUGGESTED ─┬─(accept)─▶ ACCEPTED
 *                                           ├─(reject)─▶ REJECTED
 *                                           └─(expire)─▶ EXPIRED
 *
 *   REQUESTED ─(markFailed)──▶ FAILED
 * </pre>
 *
 * <p>final 상태 (ACCEPTED / REJECTED / EXPIRED / FAILED) 에서 다른 상태로의 전이는 invariant 위반 →
 * {@link SentenceFeedbackInvalidTransitionException}.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code userId}, {@code diaryId} 는 다른 BC 의 Aggregate ID 이므로
 * primitive {@link UUID} 보유 + JPA 연관관계 / FK 미사용. {@code diaryId} 는 nullable (§5 작성 전 미리보기).
 */
public final class SentenceFeedback {

    /**
     * 사용자 거부 사유 (자유 텍스트, §15) 의 상한.
     *
     * <p>{@link Suggestion#REASON_MAX_CODE_POINTS} (= 500, AI 가 발행하는 제안 사유) 와는 의미가 다름 —
     * 본 상수는 사용자 입력 자유 텍스트의 악의적 거대 입력 차단용 상한.
     */
    public static final int REJECTION_REASON_MAX_CODE_POINTS = 1000;

    /** 시스템 실패 사유 ({@link Status#FAILED}) — 운영 로그 / 분석용 진단 메시지. */
    public static final int FAILURE_REASON_MAX_CODE_POINTS = 1000;

    private final SentenceFeedbackId id;
    private final UUID userId;
    private final UUID diaryId;            // nullable, §5
    private final SentenceText originalSentence;
    private final Tone tone;               // nullable, §10
    private Status status;
    private List<Suggestion> suggestions;  // immutable copy, replaced on markSuggested / markFailed
    private SuggestionId decisionSuggestionId;  // ACCEPTED 시 채움
    private String rejectionReason;        // REJECTED 시 채움 (nullable)
    private String failureReason;          // FAILED 시 채움
    private Instant expiresAt;             // SUGGESTED 시 채움
    private Instant decidedAt;             // ACCEPTED / REJECTED / EXPIRED / FAILED 시 채움
    private final Instant createdAt;

    private SentenceFeedback(
        SentenceFeedbackId id,
        UUID userId,
        UUID diaryId,
        SentenceText originalSentence,
        Tone tone,
        Status status,
        List<Suggestion> suggestions,
        SuggestionId decisionSuggestionId,
        String rejectionReason,
        String failureReason,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.diaryId = diaryId;  // nullable
        this.originalSentence = Objects.requireNonNull(originalSentence, "originalSentence");
        this.tone = tone;  // nullable
        this.status = Objects.requireNonNull(status, "status");
        this.suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
        this.decisionSuggestionId = decisionSuggestionId;
        this.rejectionReason = rejectionReason;
        this.failureReason = failureReason;
        this.expiresAt = expiresAt;
        this.decidedAt = decidedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ============================================================
    // Factories
    // ============================================================

    /**
     * 신규 요청 — REQUESTED 상태로 시작. {@code diaryIdOrNull} null 허용 (§5).
     */
    public static SentenceFeedback request(
        SentenceFeedbackId id,
        UUID userId,
        UUID diaryIdOrNull,
        SentenceText originalSentence,
        Tone toneOrNull,
        Instant now
    ) {
        Objects.requireNonNull(now, "now");
        return new SentenceFeedback(
            id, userId, diaryIdOrNull, originalSentence, toneOrNull,
            Status.REQUESTED, List.of(), null, null, null, null, null,
            now
        );
    }

    /**
     * Repository 복원용 — JpaEntity → Domain. invariant 검증 스킵 (DB 데이터는 이미 검증된 것으로 간주).
     */
    public static SentenceFeedback reconstitute(
        SentenceFeedbackId id,
        UUID userId,
        UUID diaryIdOrNull,
        SentenceText originalSentence,
        Tone toneOrNull,
        Status status,
        List<Suggestion> suggestions,
        SuggestionId decisionSuggestionIdOrNull,
        String rejectionReasonOrNull,
        String failureReasonOrNull,
        Instant expiresAtOrNull,
        Instant decidedAtOrNull,
        Instant createdAt
    ) {
        return new SentenceFeedback(
            id, userId, diaryIdOrNull, originalSentence, toneOrNull,
            status, suggestions,
            decisionSuggestionIdOrNull, rejectionReasonOrNull, failureReasonOrNull,
            expiresAtOrNull, decidedAtOrNull, createdAt
        );
    }

    // ============================================================
    // Behavior — 라이프사이클 메서드 (§2)
    // ============================================================

    /**
     * AI 응답 수신 — REQUESTED → SUGGESTED. suggestions 1+ 강제 (§7 — 빈 리스트는 markFailed 와 의미 분리).
     *
     * <p>{@code expiresAt} 은 외부 (Application) 가 결정 — TTL 정책 (§3 = 24h) 적용 후 전달.
     * {@code clock} 는 다른 라이프사이클 메서드와 일관된 시그니처 — 현재는 expiresAt > clock.instant() 검증에만 사용.
     */
    public void markSuggested(List<Suggestion> newSuggestions, Instant expiresAt, Clock clock) {
        requireStatus("markSuggested", Status.REQUESTED);
        Objects.requireNonNull(newSuggestions, "newSuggestions");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(clock, "clock");
        if (newSuggestions.isEmpty()) {
            throw new IllegalArgumentException("markSuggested requires at least one suggestion");
        }
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("expiresAt must be after now");
        }
        this.status = Status.SUGGESTED;
        this.suggestions = List.copyOf(newSuggestions);
        this.expiresAt = expiresAt;
    }

    /**
     * 사용자 채택 — SUGGESTED → ACCEPTED. {@code suggestionId} 가 suggestions 안에 존재해야 함 (§4).
     */
    public void accept(SuggestionId suggestionId, Clock clock) {
        requireStatus("accept", Status.SUGGESTED);
        Objects.requireNonNull(suggestionId, "suggestionId");
        Objects.requireNonNull(clock, "clock");
        boolean exists = suggestions.stream().anyMatch(s -> s.id().equals(suggestionId));
        if (!exists) {
            throw new SentenceFeedbackUnknownSuggestionException(
                "suggestionId not found in suggestions: " + suggestionId.asString()
            );
        }
        this.status = Status.ACCEPTED;
        this.decisionSuggestionId = suggestionId;
        this.decidedAt = clock.instant();
    }

    /**
     * 사용자 거부 — SUGGESTED → REJECTED. reason 은 nullable (§15 자유 텍스트). 1000 code points 상한.
     */
    public void reject(String reasonOrNull, Clock clock) {
        requireStatus("reject", Status.SUGGESTED);
        Objects.requireNonNull(clock, "clock");
        String normalized = normalizeReason(reasonOrNull);
        if (normalized != null && normalized.codePointCount(0, normalized.length()) > REJECTION_REASON_MAX_CODE_POINTS) {
            throw new IllegalArgumentException(
                "rejection reason exceeds " + REJECTION_REASON_MAX_CODE_POINTS + " code points");
        }
        this.status = Status.REJECTED;
        this.rejectionReason = normalized;
        this.decidedAt = clock.instant();
    }

    /**
     * TTL 만료 — SUGGESTED → EXPIRED. {@code clock.instant() >= expiresAt} 이어야 함 (Q8 NEEDS CHANGES).
     */
    public void expire(Clock clock) {
        requireStatus("expire", Status.SUGGESTED);
        Objects.requireNonNull(clock, "clock");
        Instant nowInstant = clock.instant();
        if (nowInstant.isBefore(expiresAt)) {
            throw new SentenceFeedbackInvalidTransitionException(
                "expire called before expiresAt: now=" + nowInstant + ", expiresAt=" + expiresAt
            );
        }
        this.status = Status.EXPIRED;
        this.decidedAt = nowInstant;
    }

    /**
     * AI 호출 실패 — REQUESTED → FAILED. fallback 메시지 합성은 응답 단계 (Application).
     */
    public void markFailed(String reason, Clock clock) {
        requireStatus("markFailed", Status.REQUESTED);
        Objects.requireNonNull(clock, "clock");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be blank");
        }
        if (reason.codePointCount(0, reason.length()) > FAILURE_REASON_MAX_CODE_POINTS) {
            throw new IllegalArgumentException(
                "failure reason exceeds " + FAILURE_REASON_MAX_CODE_POINTS + " code points");
        }
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.decidedAt = clock.instant();
    }

    // ============================================================
    // Getters
    // ============================================================

    public SentenceFeedbackId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    /** §5 — null 허용. */
    public Optional<UUID> diaryId() {
        return Optional.ofNullable(diaryId);
    }

    public SentenceText originalSentence() {
        return originalSentence;
    }

    /** §10 — null 허용 (사용자 미명시). */
    public Optional<Tone> tone() {
        return Optional.ofNullable(tone);
    }

    public Status status() {
        return status;
    }

    /** Immutable list 반환 — {@link List#copyOf} 결과로 외부 변경 차단 (UnsupportedOperationException). */
    public List<Suggestion> suggestions() {
        return suggestions;
    }

    public Optional<SuggestionId> decisionSuggestionId() {
        return Optional.ofNullable(decisionSuggestionId);
    }

    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void requireStatus(String operation, Status... allowed) {
        for (Status s : allowed) {
            if (this.status == s) {
                return;
            }
        }
        throw new SentenceFeedbackInvalidTransitionException(
            operation + " not allowed in status " + this.status
        );
    }

    private static String normalizeReason(String reasonOrNull) {
        if (reasonOrNull == null) {
            return null;
        }
        // blank-only → null (§15 자유 텍스트, 사용자가 안 적은 것과 동일 의미)
        if (reasonOrNull.isEmpty() || reasonOrNull.codePoints().allMatch(Character::isWhitespace)) {
            return null;
        }
        return reasonOrNull;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SentenceFeedback that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
