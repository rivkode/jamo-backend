package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * sentence_feedback 테이블 매핑.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code userId} / {@code diaryId} / {@code decisionSuggestionId} 는 다른
 * BC / VO 의 식별자 — JPA 연관관계 / FK constraint 미사용. {@code diary_id} 는 nullable
 * (sentence-feedback-domain-policy.md §5 — 작성 전 미리보기).
 *
 * <p><b>suggestions JSON</b>: Hibernate 6.4+ 의 {@link JdbcTypeCode}({@link SqlTypes#JSON}) 으로
 * MySQL JSON 컬럼 매핑. {@link SuggestionEmbedded} 는 plain record (UUID/String/double 만) 라 Jackson
 * 자동 직렬화 OK — 도메인 {@code Suggestion} (SuggestionId wrapper 보유) 과 분리해 wire format 단순화.
 * Mapper 가 도메인 ↔ Embedded 변환.
 *
 * <p><b>status / tone</b>: 운영 / 수동 SQL 디버깅 편의 위해 enum name() 문자열 그대로 저장 (`@Enumerated`
 * 미사용 — Mapper 에서 valueOf 변환). tone 은 nullable.
 */
@Entity
@Getter
@Table(name = "sentence_feedback")
public class SentenceFeedbackJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "diary_id", columnDefinition = "BINARY(16)")
    private UUID diaryId;

    @Column(name = "original_sentence", nullable = false, length = 255)
    private String originalSentence;

    @Column(name = "tone", length = 16)
    private String tone;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions", nullable = false, columnDefinition = "JSON")
    private List<SuggestionEmbedded> suggestions;

    @Column(name = "decision_suggestion_id", columnDefinition = "BINARY(16)")
    private UUID decisionSuggestionId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SentenceFeedbackJpaEntity() {
    }

    public SentenceFeedbackJpaEntity(
        UUID id,
        UUID userId,
        UUID diaryId,
        String originalSentence,
        String tone,
        String status,
        List<SuggestionEmbedded> suggestions,
        UUID decisionSuggestionId,
        String rejectionReason,
        String failureReason,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.diaryId = diaryId;
        this.originalSentence = originalSentence;
        this.tone = tone;
        this.status = status;
        this.suggestions = suggestions;
        this.decisionSuggestionId = decisionSuggestionId;
        this.rejectionReason = rejectionReason;
        this.failureReason = failureReason;
        this.expiresAt = expiresAt;
        this.decidedAt = decidedAt;
        this.createdAt = createdAt;
    }

    // setter 는 명시 유지 — Mapper 의 mergeInto 패턴에서 호출 (status / suggestions / decision* / *_at).
    public void setStatus(String status) { this.status = status; }
    public void setSuggestions(List<SuggestionEmbedded> suggestions) { this.suggestions = suggestions; }
    public void setDecisionSuggestionId(UUID decisionSuggestionId) { this.decisionSuggestionId = decisionSuggestionId; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    /**
     * suggestions JSON 컬럼 직렬화용 Embedded record. 도메인 {@code Suggestion} 와 같은 4 필드 의미를
     * 갖되 SuggestionId wrapper 를 풀어 plain UUID 로 저장 — JSON 가독성 + Jackson 자동 직렬화 호환.
     */
    public record SuggestionEmbedded(UUID id, String text, String reason, double confidence) {
    }
}
