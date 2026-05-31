package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentContent;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import java.util.List;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * comment Application 테스트 공통 fixture — diary core {@code DiaryTestFixtures} 정합.
 */
final class CommentTestFixtures {

    static final Instant NOW = Instant.parse("2026-04-30T10:00:00Z");

    private CommentTestFixtures() {
    }

    static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static Diary publicDiary(UUID author) {
        return Diary.create(
            DiaryId.newId(), author,
            new DiaryLines(List.of("오늘 산책", "날씨 좋다", "기분 좋음")),
            ImageUrls.empty(),
            Tags.empty(),
            Visibility.PUBLIC,
            fixedClock()
        );
    }

    static Diary privateDiary(UUID author) {
        return Diary.create(
            DiaryId.newId(), author,
            new DiaryLines(List.of("비공개 메모", "둘째 줄", "셋째 줄")),
            ImageUrls.empty(),
            Tags.empty(),
            Visibility.PRIVATE,
            fixedClock()
        );
    }

    static Comment rootComment(DiaryId diaryId, UUID author) {
        return Comment.create(
            CommentId.newId(), diaryId, author,
            new CommentContent("좋은 글이네요!"), null, fixedClock()
        );
    }

    static Comment reply(DiaryId diaryId, UUID author, CommentId parentId) {
        return Comment.create(
            CommentId.newId(), diaryId, author,
            new CommentContent("답글입니다"), parentId, fixedClock()
        );
    }

    static Comment commentWithLikes(DiaryId diaryId, UUID author, CommentId parentIdOrNull, int likeCount) {
        return Comment.reconstitute(
            CommentId.newId(), diaryId, author,
            new CommentContent("재구성"), parentIdOrNull, likeCount, NOW
        );
    }
}
