package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PUT /api/v1/diaries/{diaryId} Request body — Slice 3-a (PRD 0526_flutter.md §2.4).
 *
 * <p>{@link CreateDiaryRequest} 와 동일 schema — 전체 replace 의미 (PUT 의미상 정합). partial update (PATCH)
 * 는 본 PR 범위 밖.
 *
 * <p>Bean Validation 한도 / 정규화 / 도메인 VO 위임 정책은 {@link CreateDiaryRequest} 와 동일 — 한도 상수
 * ({@code CONTENT_MAX_CHARS=4000} / {@code TAG_MAX_CHARS=60} / {@code IMAGE_URL_MAX_CHARS=2048} /
 * {@code IMAGES_MAX_COUNT=5} / {@code TAGS_MAX_COUNT=10}) 도 그대로 차용. record annotation parameter 가
 * 컴파일 타임 상수만 허용해 두 record 가 inline 한도를 각각 표기 — 변경 시 두 record 동시 갱신 필수.
 *
 * <p>본 record 는 별도 도입 — 향후 PATCH 도입 시 필드 optional 화가 본 DTO 만 영향. 또 OpenAPI schema 가
 * CreateDiaryRequest 와 분리되어 frontend SDK 생성 시 의도 명확화.
 *
 * <p>{@code visibility} compact constructor 의 빈 문자열 → null normalization 은 {@link CreateDiaryRequest}
 * H1 박제 정합 — 정규식 변경 시 NPE 회귀 차단 가드 (현 정규식 ^(PUBLIC|PRIVATE)$ 가 이미 빈 문자열 거부하므로
 * 도달 불가 경로지만 회귀 안전망).
 *
 * <p><b>비작성자 응답 (Q-S3a-1)</b>: presentation 매핑은 404 — IDOR 통일 (사용자 결정). DiaryAccessDenied
 * Exception 도 DiaryExceptionHandler 가 404 로 매핑하므로 별도 분기 불필요.
 *
 * @param content    일기 본문 (1..4000 char 1차, 도메인 1..2000 cp)
 * @param images     이미지 URL 목록 (max 5건) — null/생략 가능 → empty list
 * @param tags       태그 목록 (max 10건) — null/생략 가능 → empty list
 * @param visibility "PUBLIC" / "PRIVATE" / null (default PUBLIC — Controller 책임)
 */
public record UpdateDiaryRequest(
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
