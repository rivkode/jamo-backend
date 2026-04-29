package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/diaries Request body.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (CreateRequest 필드 명시).
 *
 * <p>Bean Validation 1차 — char (UTF-16) 기준 상한은 도메인 code points 한도의 ×2 (surrogate pair 대비) 로
 * 빠른 거부. 도메인 VO ({@link app.backend.jamo.diary.domain.model.diary.DiaryContent} /
 * {@link app.backend.jamo.diary.domain.model.diary.Tag} /
 * {@link app.backend.jamo.diary.domain.model.diary.ImageUrls}) 가 정확한 invariant 검증 (1..2000 cp /
 * 1..30 cp / max 5 + http(s) only). sentence-feedback 정합.
 *
 * <p>{@code visibility} 는 optional — null 시 Controller 가 PUBLIC default 변환 (박제 §3, Application
 * Command 는 명시 값 강제). 빈 문자열은 null 로 normalization (compact constructor) 하여 정규식
 * 변경 시 NPE 회귀 차단.
 *
 * <p><b>1차 거부 한도</b> (code-reviewer L1 — 매직 넘버 박제):
 * <ul>
 *   <li>{@link #CONTENT_MAX_CHARS} = 4000 = {@code DiaryContent.MAX_CODE_POINTS} × 2</li>
 *   <li>{@link #TAG_MAX_CHARS} = 60 = {@code Tag.MAX_CODE_POINTS} × 2</li>
 *   <li>{@link #IMAGE_URL_MAX_CHARS} = 2048 (RFC 7230 권장 URL 한도 정합)</li>
 *   <li>{@link #IMAGES_MAX_COUNT} = 5, {@link #TAGS_MAX_COUNT} = 10 — 도메인 invariant 정합</li>
 * </ul>
 * record annotation 의 parameter 가 컴파일 타임 상수만 허용해 직접 참조는 불가 — 본 javadoc 으로 박제 cross-reference.
 *
 * @param content    일기 본문 (1..4000 char 1차, 도메인 1..2000 cp)
 * @param images     이미지 URL 목록 (max 5건, 각 URL 1..2048 char) — null/생략 가능 → empty list
 * @param tags       태그 목록 (max 10건, 각 1..60 char 1차, 도메인 1..30 cp) — null/생략 가능 → empty list
 * @param visibility "PUBLIC" / "PRIVATE" / null (default PUBLIC)
 */
public record CreateDiaryRequest(
    @NotBlank
    @Size(max = 4000, message = "content too long")
    String content,

    @Size(max = 5, message = "images max 5")
    List<@NotBlank @Size(max = 2048) String> images,

    @Size(max = 10, message = "tags max 10")
    List<@NotBlank @Size(max = 60) String> tags,

    @Pattern(regexp = "^(PUBLIC|PRIVATE)$", message = "visibility must be PUBLIC or PRIVATE")
    String visibility
) {
    public static final int CONTENT_MAX_CHARS = 4000;
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
        // code-reviewer H1 — 빈 문자열 normalization. @Pattern 이 null 은 통과시키므로
        // 빈 문자열도 null 처럼 처리 (PUBLIC default). 정규식이 ^(...)$ 로 빈 문자열을 거부해
        // 현재 도달 가능 경로는 없지만, 향후 정규식 변경 시 Visibility.valueOf("") IAE 회귀 차단.
        if (visibility != null && visibility.isBlank()) {
            visibility = null;
        }
    }
}
