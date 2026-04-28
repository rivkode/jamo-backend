package app.backend.jamo.diary.domain.model.sentencefeedback;

/**
 * 문장 피드백 요청 시 어조 힌트.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §10 + decisions/contracts/ai-assistant-service-method-catalog.md §"tone 운영 enum 후속 항목 해소".
 *
 * <p>Aggregate 는 {@code Tone?} (nullable) 보유 — null 은 "사용자 미명시" 의미. chat-service 가 default 정책 적용
 * (현재는 {@link #NEUTRAL} 와 동등). null 을 sentinel {@code NEUTRAL} 으로 합치면 "사용자 명시 neutral" vs
 * "미명시" 정보 손실 → null 보존.
 *
 * <p>chat.proto 의 {@code SentenceFeedbackRequest.tone} 은 string (proto3 enum 미사용 — finish_reason 정책
 * 정합). 서버 측 변환: null → 빈 문자열, 그 외 → {@link #name()} 의 lowercase ("casual"/"formal"/"neutral").
 *
 * <p><b>Wire format 변환 책임</b>: 본 enum 자체에는 toWireString/fromWireString 메서드 미정의 — 도메인이
 * proto wire format 을 직접 알지 않도록 Application/Mapper layer 책임 (D-a-5-impl-app 의 chat-service
 * gRPC client adapter 에서 변환). 도메인 순수성 (DDD layered) 우선.
 */
public enum Tone {
    CASUAL,
    FORMAL,
    NEUTRAL;
}
