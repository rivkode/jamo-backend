package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import app.backend.jamo.diary.infrastructure.persistence.entity.SentenceFeedbackJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.mapper.SentenceFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SentenceFeedbackRepository port 구현. ProfileRepositoryImpl 의 UPSERT 패턴 정합 (mergeInto / toJpaEntity).
 */
@Repository
@RequiredArgsConstructor
public class SentenceFeedbackRepositoryImpl implements SentenceFeedbackRepository {

    private final SpringDataSentenceFeedbackRepository jpa;

    @Override
    public void save(SentenceFeedback feedback) {
        SentenceFeedbackJpaEntity entity = jpa.findById(feedback.id().value())
            .map(existing -> SentenceFeedbackMapper.mergeInto(existing, feedback))
            .orElseGet(() -> SentenceFeedbackMapper.toJpaEntity(feedback));
        jpa.save(entity);
    }

    @Override
    public Optional<SentenceFeedback> findById(SentenceFeedbackId id) {
        return jpa.findById(id.value()).map(SentenceFeedbackMapper::toDomain);
    }

    @Override
    public Optional<SentenceFeedback> findByIdAndUserId(SentenceFeedbackId id, UUID userId) {
        return jpa.findByIdAndUserId(id.value(), userId).map(SentenceFeedbackMapper::toDomain);
    }

    @Override
    public int deleteAllByDiaryId(UUID diaryId) {
        return jpa.deleteAllByDiaryId(diaryId);
    }

    @Override
    public int deleteAllByUserId(UUID userId) {
        return jpa.deleteAllByUserId(userId);
    }

    @Override
    public List<SentenceFeedbackId> findExpirableSuggestedBefore(Instant cutoff, int limit) {
        return jpa.findExpirableSuggestedIdsForUpdate(cutoff, limit).stream()
            .map(SentenceFeedbackRepositoryImpl::bytesToUuid)
            .map(SentenceFeedbackId::of)
            .toList();
    }

    @Override
    public List<SentenceFeedbackId> findFinalOlderThan(Instant cutoff, int limit) {
        return jpa.findFinalOlderThanIdsForUpdate(cutoff, limit).stream()
            .map(SentenceFeedbackRepositoryImpl::bytesToUuid)
            .map(SentenceFeedbackId::of)
            .toList();
    }

    @Override
    public int deleteByIds(List<SentenceFeedbackId> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jpa.deleteByIdIn(ids.stream().map(SentenceFeedbackId::value).toList());
    }

    /**
     * MySQL BINARY(16) column 의 native projection 결과 (byte[]) → UUID 변환.
     * Hibernate 가 UUID 직접 매핑을 안 해주는 native query 의 한계 보완.
     */
    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
