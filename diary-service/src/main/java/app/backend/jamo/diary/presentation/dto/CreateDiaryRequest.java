package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/diaries Request body.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (CreateRequest 필드 명시).
 *
 * <p>Bean Validation 1차 — char (UTF-16) 기준 상한은 도메인 code points 한도의 ×2 (surrogate pair 대비) 로
 * 빠른 거부. 도메인 VO ({@link app.backend.jamo.diary.domain.model.diary.DiaryLines} /
 * {@link app.backend.jamo.diary.domain.model.diary.Tag} /
 * {@link app.backend.jamo.diary.domain.model.diary.ImageUrls}) 가 정확한 invariant 검증 (정확히 3줄 각 1..200 cp /
 * 1..30 cp / max 5 + http(s) only).
 *
 * <p><b>lines (PRD §2.3)</b>: 정확히 3줄, 각 1~200자. Bean Validation 의 {@code @Size} 는 List 의 element 개수
 * 검증이 가변(min/max)이라 "정확히 3" 을 강제할 수 없다 — 개수/길이 invariant 는 도메인 {@code DiaryLines} VO 가
 * 검증 (개수 위반 422 INVALID_LINE_COUNT / 길이 위반 400 INVALID_LINE_LENGTH). Request 단에서는 각 줄의 char
 * 1차 상한만.
 *
 * <p>{@code visibility} 는 optional — null 시 Controller 가 PUBLIC default 변환 (박제 §3). 빈 문자열은 null 로
 * normalization.
 *
 * @param lines      일기 3줄 본문 — 정확히 3개, 각 1~200자 (도메인 VO 강제). null 차단
 * @param images     이미지 URL 목록 (max 5건, 각 URL 1..2048 char) — null/생략 가능 → empty list
 * @param tags       태그 목록 (max 10건, 각 1..60 char 1차, 도메인 1..30 cp) — null/생략 가능 → empty list
 * @param visibility "PUBLIC" / "PRIVATE" / null (default PUBLIC)
 */
public record CreateDiaryRequest(
    @NotNull(message = "lines is required")
    List<@NotBlank @Size(max = LINE_MAX_CHARS, message = "line too long") String> lines,

    @Size(max = 5, message = "images max 5")
    List<@NotBlank @Size(max = 2048) String> images,

    @Size(max = 10, message = "tags max 10")
    List<@NotBlank @Size(max = 60) String> tags,

    @Pattern(regexp = "^(PUBLIC|PRIVATE)$", message = "visibility must be PUBLIC or PRIVATE")
    String visibility
) {
    /** 각 줄 char 1차 상한 = DiaryLines.LINE_MAX_CODE_POINTS(200) × 2 (surrogate pair 대비). */
    public static final int LINE_MAX_CHARS = 400;
    public static final int TAG_MAX_CHARS = 60;
    public static final int IMAGE_URL_MAX_CHARS = 2048;
    public static final int IMAGES_MAX_COUNT = 5;
    public static final int TAGS_MAX_COUNT = 10;

    public CreateDiaryRequest {
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
