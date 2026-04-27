package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;

/**
 * 외부 이메일 발송 port.
 *
 * <p>SMTP / SES 등 실제 인프라는 Infrastructure 어댑터에서 결정 — 본 port 는 도메인 언어로
 * 발송 의도만 표현 (PRD user/sendValidationNumber.md §9). 어댑터 구현 시 retry / circuit
 * breaker 정책은 별도 결정 문서로 박제 예정 (`docs/decisions/identity/email-sender-impl.md`).
 *
 * <p>본 port 는 비동기 동작 가정 — 호출자는 발송 실패가 즉시 예외로 surfacing 되지 않을 수
 * 있음을 인지하고, application service 단에서 적절한 fallback / 사용자 메시지를 결정한다.
 */
public interface EmailSender {

    /**
     * 이메일 검증코드 발송. 발송 실패 시 구현체의 정책(재시도/예외)에 따른다.
     */
    void sendValidationCode(Email email, ValidationCode code);
}
