package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryLines;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * diary core Application 테스트 공통 fixture — sentence-feedback {@code SentenceFeedbackTestFixtures} 정합.
 */
final class DiaryTestFixtures {

    static final Instant NOW = Instant.parse("2026-04-30T10:00:00Z");

    private DiaryTestFixtures() {
    }

    static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static Diary publicDiary(UUID author) {
        return Diary.create(
            DiaryId.newId(), author,
            new DiaryLines(List.of("오늘 산책", "날씨 좋다", "기분 좋음")),
            ImageUrls.empty(),
            Tags.ofStrings(List.of("일상")),
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

    static Diary publicDiaryAt(UUID author, Instant createdAt, int likeCount) {
        return Diary.reconstitute(
            DiaryId.newId(), author,
            new DiaryLines(List.of("ok", "line2", "line3")),
            ImageUrls.empty(),
            Tags.empty(),
            Visibility.PUBLIC,
            likeCount, 0,
            createdAt
        );
    }
}
