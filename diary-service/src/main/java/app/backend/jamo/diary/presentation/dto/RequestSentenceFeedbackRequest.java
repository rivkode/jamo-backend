package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/diaries/sentence-feedback Request body.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §9 (입력 검증).
 *
 * <p>Bean Validation 1차 — 길이 char (UTF-16) 기준 빠른 거부. 도메인 {@link
 * app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText} VO 가 1..50 <b>code points</b>
 * 정확 검증 (이모지/한자 surrogate pair 정합). priorSentences 상한 5건 (박제 §9).
 *
 * @param sentence       피드백 대상 문장 (1..50 code points, blank 차단)
 * @param diaryId        일기 ID UUID 문자열 (선택, 작성 전 미리보기 시 null)
 * @param priorSentences 앞 문장 컨텍스트 (선택, 최대 5건)
 * @param tone           "casual" / "formal" / "neutral" (선택, unknown 은 forward 호환으로 무시)
 */
public record RequestSentenceFeedbackRequest(
    @NotBlank
    @Size(max = 200, message = "sentence too long")  // char 기준 1차 거부 — 도메인 VO 가 50 cp 정확 검증
    String sentence,

    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "diaryId must be UUID")
    String diaryId,

    @Size(max = 5, message = "priorSentences max 5")
    List<@Size(max = 200) String> priorSentences,

    @Size(max = 16)
    String tone
) {
    public RequestSentenceFeedbackRequest {
        if (priorSentences == null) {
            priorSentences = List.of();
        }
    }
}
