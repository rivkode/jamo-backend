package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.DiaryLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DiaryLikeRepository port 구현.
 *
 * <p>UNIQUE 제약 (`uk_diary_like_diary_user`) 위반 시 Spring 의 {@code DataIntegrityViolationException} 으로
 * 전파 — Application Service 의 사전 {@code existsByDiaryIdAndUserId} 체크와 함께 동시성 race window 의 최후 보루.
 */
@Repository
@RequiredArgsConstructor
public class DiaryLikeRepositoryImpl implements DiaryLikeRepository {

    private final SpringDataDiaryLikeRepository jpa;

    @Override
    public void save(DiaryLike like) {
        jpa.save(DiaryLikeMapper.toJpaEntity(like));
    }

    @Override
    public Optional<DiaryLike> findByDiaryIdAndUserId(DiaryId diaryId, UUID userId) {
        return jpa.findByDiaryIdAndUserId(diaryId.value(), userId).map(DiaryLikeMapper::toDomain);
    }

    @Override
    public boolean existsByDiaryIdAndUserId(DiaryId diaryId, UUID userId) {
        return jpa.existsByDiaryIdAndUserId(diaryId.value(), userId);
    }

    @Override
    public void deleteByDiaryIdAndUserId(DiaryId diaryId, UUID userId) {
        jpa.deleteByDiaryIdAndUserIdReturnAffected(diaryId.value(), userId);
    }

    @Override
    public long countByDiaryId(DiaryId diaryId) {
        return jpa.countByDiaryId(diaryId.value());
    }

    @Override
    public Set<DiaryId> findDiaryIdsLikedByUser(UUID userId, Set<DiaryId> diaryIds) {
        if (diaryIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> rawIds = diaryIds.stream().map(DiaryId::value).collect(Collectors.toSet());
        return new HashSet<>(jpa.findLikedDiaryIds(userId, rawIds))
            .stream().map(DiaryId::of).collect(Collectors.toSet());
    }

    @Override
    public int deleteAllByDiaryId(DiaryId diaryId) {
        return jpa.deleteAllByDiaryId(diaryId.value());
    }

    @Override
    public int deleteAllByUserId(UUID userId) {
        return jpa.deleteAllByUserId(userId);
    }
}
