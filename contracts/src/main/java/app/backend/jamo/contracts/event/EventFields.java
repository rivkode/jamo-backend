package app.backend.jamo.contracts.event;

/**
 * Kafka 이벤트 record 의 공통 필드 검증 헬퍼.
 *
 * <p>contracts 모듈은 의존성 최소화 (ADR-0002) — Apache Commons / Guava 등 외부 라이브러리 미사용.
 * 자체 헬퍼로 반복 검증 로직 추출 + 메시지 형식 일관성 보장.
 *
 * <p>모든 record 는 compact constructor 에서 본 헬퍼를 사용해 invariant 를 검증한다.
 * `event/<bc>/` 하위 record 들이 사용하므로 public — contracts 모듈 외부 노출 자체가 적합 (검증 헬퍼는 표준 패턴).
 */
public final class EventFields {

    private EventFields() {
    }

    /** {@code value} 가 null 또는 빈 / 공백 문자열 ({@link String#isBlank()}) 이면 IllegalArgumentException. */
    public static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /** {@code value} 가 null 이면 IllegalArgumentException. */
    public static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /** {@code value} 가 음수면 IllegalArgumentException. 0 은 허용. */
    public static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
