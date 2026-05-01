package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.commentlike.CommentLike;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.CommentLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CommentLikeRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryImpl implements CommentLikeRepository {

    private final SpringDataCommentLikeRepository jpa;

    @Override
    public void save(CommentLike like) {
        jpa.save(CommentLikeMapper.toJpaEntity(like));
    }

    @Override
    public Optional<CommentLike> findByCommentIdAndUserId(CommentId commentId, UUID userId) {
        return jpa.findByCommentIdAndUserId(commentId.value(), userId).map(CommentLikeMapper::toDomain);
    }

    @Override
    public boolean existsByCommentIdAndUserId(CommentId commentId, UUID userId) {
        return jpa.existsByCommentIdAndUserId(commentId.value(), userId);
    }

    @Override
    public void deleteByCommentIdAndUserId(CommentId commentId, UUID userId) {
        jpa.deleteByCommentIdAndUserIdReturnAffected(commentId.value(), userId);
    }

    @Override
    public Set<CommentId> findCommentIdsLikedByUser(UUID userId, Set<CommentId> commentIds) {
        if (commentIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> rawIds = commentIds.stream().map(CommentId::value).collect(Collectors.toSet());
        return new HashSet<>(jpa.findLikedCommentIds(userId, rawIds))
            .stream().map(CommentId::of).collect(Collectors.toSet());
    }

    @Override
    public int deleteAllByCommentId(CommentId commentId) {
        return jpa.deleteAllByCommentId(commentId.value());
    }

    @Override
    public int deleteAllByCommentParentId(CommentId parentId) {
        return jpa.deleteAllByCommentParentId(parentId.value());
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
