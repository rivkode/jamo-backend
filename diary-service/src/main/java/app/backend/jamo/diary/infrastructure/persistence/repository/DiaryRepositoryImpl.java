package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.mapper.DiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DiaryRepository port 구현. SentenceFeedbackRepositoryImpl 의 UPSERT 패턴 정합 (mergeInto / toJpaEntity).
 */
@Repository
@RequiredArgsConstructor
public class DiaryRepositoryImpl implements DiaryRepository {

    private final SpringDataDiaryRepository jpa;

    @Override
    public void save(Diary diary) {
        DiaryJpaEntity entity = jpa.findById(diary.id().value())
            .map(existing -> DiaryMapper.mergeInto(existing, diary))
            .orElseGet(() -> DiaryMapper.toJpaEntity(diary));
        jpa.save(entity);
    }

    @Override
    public Optional<Diary> findById(DiaryId id) {
        return jpa.findById(id.value()).map(DiaryMapper::toDomain);
    }

    @Override
    public Optional<Diary> findByIdForUpdate(DiaryId id) {
        return jpa.findByIdForUpdate(id.value()).map(DiaryMapper::toDomain);
    }

    @Override
    public boolean existsById(DiaryId id) {
        return jpa.existsById(id.value());
    }

    @Override
    public void deleteById(DiaryId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public List<Diary> findPublicFeedRecent(Optional<Tag> tag, RecentFeedCursor cursorOrNull, int limit) {
        List<DiaryJpaEntity> rows;
        if (tag.isPresent()) {
            String tagValue = tag.get().value();
            rows = (cursorOrNull == null)
                ? jpa.findPublicFeedRecentByTagFirst(tagValue, limit)
                : jpa.findPublicFeedRecentByTagAfter(tagValue,
                    cursorOrNull.lastCreatedAt(), cursorOrNull.lastDiaryId().value(), limit);
        } else {
            rows = (cursorOrNull == null)
                ? jpa.findPublicFeedRecentFirst(limit)
                : jpa.findPublicFeedRecentAfter(
                    cursorOrNull.lastCreatedAt(), cursorOrNull.lastDiaryId().value(), limit);
        }
        return rows.stream().map(DiaryMapper::toDomain).toList();
    }

    @Override
    public List<Diary> findPublicFeedPopular(Optional<Tag> tag, PopularFeedCursor cursorOrNull, int limit) {
        List<DiaryJpaEntity> rows;
        if (tag.isPresent()) {
            String tagValue = tag.get().value();
            rows = (cursorOrNull == null)
                ? jpa.findPublicFeedPopularByTagFirst(tagValue, limit)
                : jpa.findPublicFeedPopularByTagAfter(tagValue,
                    cursorOrNull.lastLikeCount(), cursorOrNull.lastCreatedAt(),
                    cursorOrNull.lastDiaryId().value(), limit);
        } else {
            rows = (cursorOrNull == null)
                ? jpa.findPublicFeedPopularFirst(limit)
                : jpa.findPublicFeedPopularAfter(
                    cursorOrNull.lastLikeCount(), cursorOrNull.lastCreatedAt(),
                    cursorOrNull.lastDiaryId().value(), limit);
        }
        return rows.stream().map(DiaryMapper::toDomain).toList();
    }

    @Override
    public List<Diary> findMyFeedRecent(UUID authorId, RecentFeedCursor cursorOrNull, int limit) {
        List<DiaryJpaEntity> rows = (cursorOrNull == null)
            ? jpa.findMyFeedRecentFirst(authorId, limit)
            : jpa.findMyFeedRecentAfter(authorId,
                cursorOrNull.lastCreatedAt(), cursorOrNull.lastDiaryId().value(), limit);
        return rows.stream().map(DiaryMapper::toDomain).toList();
    }

    @Override
    public int deleteAllByAuthorId(UUID authorId) {
        return jpa.deleteAllByAuthorId(authorId);
    }
}
