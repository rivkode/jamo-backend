package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;
import app.backend.jamo.diary.infrastructure.persistence.entity.CommentJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CommentRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final SpringDataCommentRepository jpa;

    @Override
    public void save(Comment comment) {
        CommentJpaEntity entity = jpa.findById(comment.id().value())
            .map(existing -> CommentMapper.mergeInto(existing, comment))
            .orElseGet(() -> CommentMapper.toJpaEntity(comment));
        jpa.save(entity);
    }

    @Override
    public Optional<Comment> findById(CommentId id) {
        return jpa.findById(id.value()).map(CommentMapper::toDomain);
    }

    @Override
    public Optional<Comment> findByIdWithoutLock(CommentId id) {
        return jpa.findByIdWithoutLock(id.value()).map(CommentMapper::toDomain);
    }

    @Override
    public boolean existsById(CommentId id) {
        return jpa.existsById(id.value());
    }

    @Override
    public void deleteById(CommentId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public List<Comment> findByDiaryId(DiaryId diaryId, CommentCursor cursorOrNull, int limit) {
        List<CommentJpaEntity> rows = cursorOrNull == null
            ? jpa.findByDiaryIdFirst(diaryId.value(), limit)
            : jpa.findByDiaryIdAfter(
                diaryId.value(), cursorOrNull.lastCreatedAt(), cursorOrNull.lastCommentId().value(), limit);
        return rows.stream().map(CommentMapper::toDomain).toList();
    }

    @Override
    public List<Comment> findChildrenByParentIdForUpdate(CommentId parentId) {
        return jpa.findChildrenByParentIdForUpdate(parentId.value())
            .stream()
            .map(CommentMapper::toDomain)
            .toList();
    }

    @Override
    public int deleteAllByParentId(CommentId parentId) {
        return jpa.deleteAllByParentId(parentId.value());
    }

    @Override
    public int deleteAllByDiaryId(DiaryId diaryId) {
        return jpa.deleteAllByDiaryId(diaryId.value());
    }

    @Override
    public int deleteAllByAuthorId(UUID authorId) {
        return jpa.deleteAllByAuthorId(authorId);
    }
}
