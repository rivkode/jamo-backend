package app.backend.jamo.identity.domain.model.user;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일 검증 6자리 숫자 코드 VO.
 *
 * <p>인증 자격증명 성격이므로 {@link SecureRandom} 으로 생성한다 (PRD user/sendValidationNumber.md §9).
 * 외부에서 받은 raw 코드는 {@link #of(String)} 로 검증, 신규 발급은 {@link #generate()}.
 */
public record ValidationCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^\\d{6}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANGE = 1_000_000;

    public ValidationCode {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("validation code must be exactly 6 digits");
        }
    }

    public static ValidationCode generate() {
        return new ValidationCode("%06d".formatted(RANDOM.nextInt(RANGE)));
    }

    public static ValidationCode of(String raw) {
        return new ValidationCode(raw);
    }
}
