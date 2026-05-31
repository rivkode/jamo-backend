package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PUT /api/v1/diaries/{diaryId} Request body — Slice 3-a (PRD 0526_flutter.md §2.4).
 *
 * <p>{@link CreateDiaryRequest} 와 동일 schema — 전체 replace 의미 (PUT 의미상 정합). partial update (PATCH)
 * 는 본 PR 범위 밖. lines 정확히 3줄 / 각 1~200자 invariant 는 도메인 {@code DiaryLines} VO 가 검증
 * (개수 422 / 길이 400). 한도 상수는 {@link CreateDiaryRequest} 와 동일 — 변경 시 두 record 동시 갱신.
 *
 * <p><b>비작성자 응답 (Q-S3a-1)</b>: presentation 매핑은 404 — IDOR 통일 (사용자 결정).
 *
 * @param lines      일기 3줄 본문 — 정확히 3개, 각 1~200자 (도메인 VO 강제). null 차단
 * @param images     이미지 URL 목록 (max 5건) — null/생략 가능 → empty list
 * @param tags       태그 목록 (max 10건) — null/생략 가능 → empty list
 * @param visibility "PUBLIC" / "PRIVATE" / null (default PUBLIC — Controller 책임)
 */
public record UpdateDiaryRequest(
    @NotNull(message = "lines is required")
    List<@NotBlank @Size(max = CreateDiaryRequest.LINE_MAX_CHARS, message = "line too long") String> lines,

    @Size(max = 5, message = "images max 5")
    List<@NotBlank @Size(max = 2048) String> images,

    @Size(max = 10, message = "tags max 10")
    List<@NotBlank @Size(max = 60) String> tags,

    @Pattern(regexp = "^(PUBLIC|PRIVATE)$", message = "visibility must be PUBLIC or PRIVATE")
    String visibility
) {
    public UpdateDiaryRequest {
        if (images == null) {
            images = List.of();
        }
        if (tags == null) {
            tags = List.of();
        }
        if (visibility != null && visibility.isBlank()) {
            visibility = null;
        }
    }
}
