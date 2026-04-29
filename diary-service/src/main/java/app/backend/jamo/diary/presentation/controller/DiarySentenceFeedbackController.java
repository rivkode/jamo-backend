package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.sentencefeedback.AcceptSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.RejectSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.RequestSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.application.service.sentencefeedback.AcceptSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.RejectSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.RequestSentenceFeedbackService;
import app.backend.jamo.diary.presentation.dto.AcceptSentenceFeedbackRequest;
import app.backend.jamo.diary.presentation.dto.RejectSentenceFeedbackRequest;
import app.backend.jamo.diary.presentation.dto.RequestSentenceFeedbackRequest;
import app.backend.jamo.diary.presentation.dto.SentenceFeedbackResponse;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * sentence-feedback HTTP API (PRD diary/{request,accept,reject}SentenceFeedback.md).
 *
 * <p>3 endpoint:
 * <ul>
 *   <li>POST /api/v1/diaries/sentence-feedback — AI 피드백 요청 (200 + status SUGGESTED|FAILED)</li>
 *   <li>POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept — 제안 채택 (200 + status ACCEPTED)</li>
 *   <li>POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject — 제안 거부 (204 No Content)</li>
 * </ul>
 *
 * <p>모두 인증 필수 ({@code @LoginUser}). path / body UUID 는 문자열로 받아 Controller 에서 변환.
 * 변환 실패 (invalid UUID) 시 IAE → ExceptionHandler 가 400 매핑.
 */
@RestController
@RequestMapping("/api/v1/diaries/sentence-feedback")
@SecurityRequirement(name = "BearerJwt")
@RequiredArgsConstructor
public class DiarySentenceFeedbackController {

    private final RequestSentenceFeedbackService requestService;
    private final AcceptSentenceFeedbackService acceptService;
    private final RejectSentenceFeedbackService rejectService;

    @PostMapping
    public ResponseEntity<SentenceFeedbackResponse> requestFeedback(
        @LoginUser AuthenticatedUser auth,
        @Valid @RequestBody RequestSentenceFeedbackRequest body
    ) {
        UUID diaryId = body.diaryId() == null ? null : UUID.fromString(body.diaryId());
        SentenceFeedbackResult result = requestService.request(new RequestSentenceFeedbackCommand(
            auth.userId(),
            diaryId,
            body.sentence(),
            body.priorSentences(),
            body.tone()
        ));
        return ResponseEntity.ok(SentenceFeedbackResponse.from(result));
    }

    @PostMapping("/{feedbackId}/accept")
    public ResponseEntity<SentenceFeedbackResponse> acceptFeedback(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("feedbackId") String feedbackId,
        @Valid @RequestBody AcceptSentenceFeedbackRequest body
    ) {
        UUID feedbackUuid = UUID.fromString(feedbackId);
        UUID suggestionUuid = UUID.fromString(body.suggestionId());
        SentenceFeedbackResult result = acceptService.accept(new AcceptSentenceFeedbackCommand(
            auth.userId(),
            feedbackUuid,
            suggestionUuid
        ));
        return ResponseEntity.ok(SentenceFeedbackResponse.from(result));
    }

    @PostMapping("/{feedbackId}/reject")
    public ResponseEntity<Void> rejectFeedback(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("feedbackId") String feedbackId,
        @Valid @RequestBody(required = false) RejectSentenceFeedbackRequest body
    ) {
        UUID feedbackUuid = UUID.fromString(feedbackId);
        String reason = body == null ? null : body.reason();
        rejectService.reject(new RejectSentenceFeedbackCommand(
            auth.userId(),
            feedbackUuid,
            reason
        ));
        // §8 PRD KEEP — 204 No Content (Application Result 폐기, HTTP 코드 차이는 Controller 책임)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
