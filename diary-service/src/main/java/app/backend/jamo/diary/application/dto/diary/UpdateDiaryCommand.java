package app.backend.jamo.diary.application.dto.diary;

import app.backend.jamo.diary.domain.model.diary.Visibility;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 일기 수정 use case 입력 (PRD 0526_flutter.md §2.4 / Slice 3-a).
 *
 * <p>전체 replace 의미 — lines / images / tags / visibility 모두 새 값. likeCount / commentCount /
 * createdAt 은 보존 (도메인 Aggregate.update invariant).
 *
 * <p><b>비작성자 응답 정책 (Q-S3a-1)</b>: editor != author 인 경우 도메인이 {@code DiaryAccessDeniedException}
 * 을 던지고, presentation 에서 404 통일 (사용자 결정 — IDOR 보호). {@link CreateDiaryCommand} 와 달리 본
 * record 는 {@code editorId} 를 별도 필드로 보유 — `authorId` 와 의미적으로 다른 (시도자) 명확화.
 *
 * @param diaryId    수정 대상
 * @param editorId   호출자 user id (작성자 검증 키)
 * @param lines      raw 3줄 본문 — VO 생성 시 invariant 검증 (정확히 3줄, 각 1~200cp)
 * @param images     raw image URLs
 * @param tags       raw tags
 * @param visibility 명시 값 강제 (null 차단 — Presentation 책임)
 */
public record UpdateDiaryCommand(
    UUID diaryId,
    UUID editorId,
    List<String> lines,
    List<String> images,
    List<String> tags,
    Visibility visibility
) {

    public UpdateDiaryCommand {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(editorId, "editorId");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(visibility, "visibility");
    }
}
