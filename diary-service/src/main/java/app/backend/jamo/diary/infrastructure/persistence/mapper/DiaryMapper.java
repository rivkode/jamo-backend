package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryJpaEntity;

import java.util.List;

/**
 * Diary Aggregate ↔ JpaEntity 변환.
 *
 * <p>SentenceFeedbackMapper 패턴 정합 — toJpaEntity / mergeInto / toDomain 3 메서드.
 *
 * <p><b>본문 lines ↔ line1/2/3 매핑</b> (ddd-architect): 도메인은 {@link DiaryLines}(단일 {@code List<String>}
 * 3개), 영속화는 {@code line1/line2/line3} 3컬럼. impedance mismatch 를 Mapper 가 흡수. {@code values().get(0/1/2)}
 * 접근은 {@code DiaryLines} 의 size==3 invariant 가 보장하므로 안전. {@code toDomain} 의 {@code List.of(...)}
 * 는 line N 이 null 이면 NPE — DB 3컬럼 NOT NULL 전제 (V10).
 *
 * <p><b>mergeInto 범위</b>: {@code likeCount} / {@code commentCount} (Aggregate behavior) + 본문(line1/2/3) /
 * {@code images} / {@code tags} / {@code visibility} (Slice 3-a 작성자 수정, PRD 0526_flutter.md §2.4 — PUT
 * /diaries/{id}). {@code id} / {@code authorId} / {@code createdAt} 은 immutable.
 */
public final class DiaryMapper {

    private DiaryMapper() {
    }

    public static DiaryJpaEntity toJpaEntity(Diary diary) {
        List<String> lines = diary.lines().values();
        return new DiaryJpaEntity(
            diary.id().value(),
            diary.authorId(),
            lines.get(0),
            lines.get(1),
            lines.get(2),
            diary.images().values(),
            diary.tags().asStrings(),
            diary.visibility().name(),
            diary.likeCount(),
            diary.commentCount(),
            diary.createdAt()
        );
    }

    /**
     * 기존 row 갱신용 — JPA managed entity 의 mutable setter 호출.
     *
     * <p>Slice 3-a 부터 Aggregate.update 가 본문(lines) / images / tags / visibility 를 변경하므로 본 메서드도
     * 갱신을 포함한다. likeCount / commentCount 는 좋아요 / 댓글 mutator 와 함께 동기화.
     *
     * <p>{@code id} / {@code authorId} / {@code createdAt} 은 immutable — 본 메서드에서 갱신하지 않는다.
     */
    public static DiaryJpaEntity mergeInto(DiaryJpaEntity existing, Diary diary) {
        List<String> lines = diary.lines().values();
        existing.setLine1(lines.get(0));
        existing.setLine2(lines.get(1));
        existing.setLine3(lines.get(2));
        existing.setImages(diary.images().values());
        existing.setTags(diary.tags().asStrings());
        existing.setVisibility(diary.visibility().name());
        existing.setLikeCount(diary.likeCount());
        existing.setCommentCount(diary.commentCount());
        return existing;
    }

    public static Diary toDomain(DiaryJpaEntity entity) {
        return Diary.reconstitute(
            DiaryId.of(entity.getId()),
            entity.getAuthorId(),
            new DiaryLines(List.of(entity.getLine1(), entity.getLine2(), entity.getLine3())),
            new ImageUrls(entity.getImages()),
            Tags.ofStrings(entity.getTags()),
            Visibility.valueOf(entity.getVisibility()),
            entity.getLikeCount(),
            entity.getCommentCount(),
            entity.getCreatedAt()
        );
    }
}
