package app.backend.jamo.diary.infrastructure.messaging;

/**
 * diary-service 가 발행 / 구독하는 Kafka 토픽 상수.
 *
 * <p>contracts JavaDoc 의 토픽 명세 정합:
 * <ul>
 *   <li>{@link #DIARY_EVENTS} — DiaryDeleted / SentenceFeedback*3 / DiaryCreated / CommentCreated 등</li>
 *   <li>{@link #USER_EVENTS} — UserWithdrawalRequested (subscribe) / UserDataPurged (publish)</li>
 * </ul>
 */
public final class KafkaTopics {

    public static final String DIARY_EVENTS = "diary-events";
    public static final String USER_EVENTS = "user-events";

    private KafkaTopics() {
    }
}
