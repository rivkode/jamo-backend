package app.backend.jamo.diary.application.dto.diary;

import app.backend.jamo.diary.domain.model.diary.Visibility;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 일기 작성 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 / §4.
 *
 * <p>도메인 invariant 검증은 VO 생성 시점에 위임 (DiaryLines / Tags / ImageUrls). 본 record 는 단순 운반.
 *
 * <p>{@code visibility} default 는 Presentation 책임 (PUBLIC) — 본 record 는 명시 값 강제 (null 차단).
 */
public record CreateDiaryCommand(
    UUID authorId,
    List<String> lines,
    List<String> images,
    List<String> tags,
    Visibility visibility
) {

    public CreateDiaryCommand {
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(visibility, "visibility");
    }
}
