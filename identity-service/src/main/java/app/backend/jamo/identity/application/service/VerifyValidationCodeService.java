package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.VerifyValidationCodeCommand;
import app.backend.jamo.identity.domain.exception.ValidationCodeExpiredException;
import app.backend.jamo.identity.domain.exception.ValidationCodeLockedException;
import app.backend.jamo.identity.domain.exception.ValidationCodeMismatchException;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.EmailValidatedFlag;
import app.backend.jamo.identity.domain.repository.ValidationCodeStore;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * 이메일 검증코드 대조 use case (PRD user/validateEmail.md §9).
 *
 * <p>흐름:
 * <ol>
 *   <li>저장된 코드 조회 — 없거나 만료 → {@link ValidationCodeExpiredException}.</li>
 *   <li>입력 코드와 constant-time 비교.</li>
 *   <li>불일치 → attempts 증가. 한도(properties.maxAttempts) 도달 시 코드 invalidate +
 *       {@link ValidationCodeLockedException}, 미만 시 {@link ValidationCodeMismatchException}.</li>
 *   <li>일치 → 코드 invalidate + {@code email_validated} flag mark (createUser 사전조건).</li>
 * </ol>
 *
 * <p><b>constant-time 비교</b>: timing oracle 회피 (CWE-208). 코드가 6자리로 짧고 rate limit
 * 으로 추측 시도가 제한적이지만 일관성 있는 보안 정책.
 *
 * <p>본 service 는 Redis 만 다루므로 {@code @Transactional} 미보유.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyValidationCodeService {

    private final ValidationCodeStore codeStore;
    private final EmailValidatedFlag validatedFlag;
    private final EmailValidationProperties properties;

    public void verify(VerifyValidationCodeCommand command) {
        Email email = new Email(command.email());
        ValidationCode submitted = ValidationCode.of(command.code());

        Optional<ValidationCode> stored = codeStore.find(email);
        if (stored.isEmpty()) {
            throw new ValidationCodeExpiredException("validation code expired or not issued");
        }

        if (!constantTimeEquals(stored.get().value(), submitted.value())) {
            int attempts = codeStore.incrementAttempts(email);
            if (attempts >= properties.maxAttempts()) {
                codeStore.invalidate(email);
                log.warn("validation code locked emailHash={} attempts={}", email.value().hashCode(), attempts);
                // 메시지에 attempts 미포함 — presentation 응답으로 노출되어선 안 됨 (security review H2).
                // 서버 로그에는 위 log.warn 으로 남김.
                throw new ValidationCodeLockedException("validation code locked");
            }
            log.warn("validation code mismatch emailHash={} attempts={}", email.value().hashCode(), attempts);
            throw new ValidationCodeMismatchException("validation code mismatch");
        }

        codeStore.invalidate(email);
        validatedFlag.mark(email, properties.validatedFlagTtl());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
