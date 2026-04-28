package app.backend.jamo.diary.application.dto.sentencefeedback;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 문장 피드백 요청 use case 입력.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §5 (diaryId nullable) / §9 (1..50 cp) /
 * §10 (tone enum casual/formal/neutral, null 허용) / §11 (priorSentences max 5).
 *
 * @param userId          호출자 사용자 ID (필수)
 * @param diaryIdOrNull   대상 일기 ID — 작성 전 미리보기 흐름은 null
 * @param sentence        문장 원문 (Service 가 SentenceText VO 변환 시 검증)
 * @param priorSentences  앞 문장 컨텍스트 (max 5, 빈 리스트 가능)
 * @param toneOrNull      어조 힌트 — "casual" / "formal" / "neutral" / null
 */
public record RequestSentenceFeedbackCommand(
    UUID userId,
    UUID diaryIdOrNull,
    String sentence,
    List<String> priorSentences,
    String toneOrNull
) {
    public RequestSentenceFeedbackCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sentence, "sentence");
        Objects.requireNonNull(priorSentences, "priorSentences");
        priorSentences = List.copyOf(priorSentences);
    }
}
