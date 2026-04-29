package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.sentencefeedback.AcceptSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.RejectSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.RequestSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.application.dto.sentencefeedback.SuggestionView;
import app.backend.jamo.diary.application.service.sentencefeedback.AcceptSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.RejectSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.RequestSentenceFeedbackService;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackRateLimitedException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackUnknownSuggestionException;
import app.backend.jamo.diary.presentation.exception.SentenceFeedbackExceptionHandler;
import app.backend.jamo.diary.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.diary.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DiarySentenceFeedbackController.class)
@Import({SentenceFeedbackExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class DiarySentenceFeedbackControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FEEDBACK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUGGESTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RequestSentenceFeedbackService requestService;
    @MockitoBean private AcceptSentenceFeedbackService acceptService;
    @MockitoBean private RejectSentenceFeedbackService rejectService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)
        ));
    }

    private SentenceFeedbackResult suggestedResult() {
        return new SentenceFeedbackResult(
            FEEDBACK_ID, "SUGGESTED", "오늘 날씨 좋아요",
            List.of(new SuggestionView(SUGGESTION_ID, "오늘은 날씨가 좋네요", "정중한 표현", 0.92)),
            null, NOW.plusSeconds(24 * 3600), NOW
        );
    }

    private SentenceFeedbackResult acceptedResult() {
        return new SentenceFeedbackResult(
            FEEDBACK_ID, "ACCEPTED", "오늘 날씨 좋아요",
            List.of(new SuggestionView(SUGGESTION_ID, "오늘은 날씨가 좋네요", "정중한 표현", 0.92)),
            SUGGESTION_ID, NOW.plusSeconds(24 * 3600), NOW
        );
    }

    // ============================================================
    // POST /api/v1/diaries/sentence-feedback (request)
    // ============================================================

    @Test
    void request_returns_200_with_SUGGESTED_response_7_fields() throws Exception {
        mockValidAuth();
        when(requestService.request(any())).thenReturn(suggestedResult());

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"오늘 날씨 좋아요","tone":"casual"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedbackId").value(FEEDBACK_ID.toString()))
            .andExpect(jsonPath("$.status").value("SUGGESTED"))
            .andExpect(jsonPath("$.originalSentence").value("오늘 날씨 좋아요"))
            .andExpect(jsonPath("$.suggestions[0].suggestionId").value(SUGGESTION_ID.toString()))
            .andExpect(jsonPath("$.suggestions[0].text").value("오늘은 날씨가 좋네요"))
            .andExpect(jsonPath("$.suggestions[0].confidence").value(0.92))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.processedAt").exists());

        ArgumentCaptor<RequestSentenceFeedbackCommand> captor =
            ArgumentCaptor.forClass(RequestSentenceFeedbackCommand.class);
        verify(requestService).request(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().toneOrNull()).isEqualTo("casual");
        assertThat(captor.getValue().sentence()).isEqualTo("오늘 날씨 좋아요");
    }

    @Test
    void request_returns_401_with_UNAUTHORIZED_code_and_no_internal_message_when_no_auth_header() throws Exception {
        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"hi"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_401_when_jwt_verify_fails_without_leaking_cause() throws Exception {
        when(jwtVerifier.verify("bad-token"))
            .thenThrow(new JwtVerificationException("token expired at 2026-04-29"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"hi"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            // sanitize — JwtVerificationException 의 raw message ("token expired at 2026-04-29") 비노출
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_400_when_sentence_blank() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"   "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_400_when_priorSentences_exceeds_5() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"hi","priorSentences":["a","b","c","d","e","f"]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_400_when_diaryId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"hi","diaryId":"not-uuid"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_400_when_body_malformed_json() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        // test-reviewer H2 — 다른 400 시나리오와 일관성 (controller body 바인딩 전 거부 검증)
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_400_when_sentence_exceeds_200_char_bean_validation() throws Exception {
        // test-reviewer M3 — Bean Validation 1차 거부 (도메인 50 cp 검증 도달 전)
        mockValidAuth();
        String tooLong = "x".repeat(201);

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"%s"}
                    """.formatted(tooLong)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(requestService, never()).request(any());
    }

    @Test
    void request_returns_429_when_rate_limited() throws Exception {
        mockValidAuth();
        when(requestService.request(any()))
            .thenThrow(new SentenceFeedbackRateLimitedException("rate limit exceeded"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sentence":"hi"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_RATE_LIMITED"))
            // test-reviewer M1 — Validation 통과 후 비즈니스 예외 분기 박제 + sanitization 회귀 신호
            // (도메인 raw message "rate limit exceeded" 가 응답에 누설되지 않음).
            .andExpect(jsonPath("$.message").value("sentence feedback request limit exceeded"));
        verify(requestService).request(any());
    }

    // ============================================================
    // POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept
    // ============================================================

    @Test
    void accept_returns_200_with_ACCEPTED_status_and_decisionSuggestionId() throws Exception {
        mockValidAuth();
        when(acceptService.accept(any())).thenReturn(acceptedResult());

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/accept", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"%s"}
                    """.formatted(SUGGESTION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.decisionSuggestionId").value(SUGGESTION_ID.toString()));

        ArgumentCaptor<AcceptSentenceFeedbackCommand> captor =
            ArgumentCaptor.forClass(AcceptSentenceFeedbackCommand.class);
        verify(acceptService).accept(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().feedbackId()).isEqualTo(FEEDBACK_ID);
        assertThat(captor.getValue().suggestionId()).isEqualTo(SUGGESTION_ID);
    }

    @Test
    void accept_returns_404_when_NotFound_or_other_user_IDOR() throws Exception {
        mockValidAuth();
        when(acceptService.accept(any()))
            .thenThrow(new SentenceFeedbackNotFoundException("not found"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/accept", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"%s"}
                    """.formatted(SUGGESTION_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_NOT_FOUND"));
    }

    @Test
    void accept_returns_409_when_already_final() throws Exception {
        mockValidAuth();
        when(acceptService.accept(any()))
            .thenThrow(new SentenceFeedbackInvalidTransitionException("already accepted"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/accept", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"%s"}
                    """.formatted(SUGGESTION_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_INVALID_TRANSITION"));
    }

    @Test
    void accept_returns_400_when_unknown_suggestionId() throws Exception {
        mockValidAuth();
        when(acceptService.accept(any()))
            .thenThrow(new SentenceFeedbackUnknownSuggestionException("not in suggestions"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/accept", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"%s"}
                    """.formatted(SUGGESTION_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION"));
    }

    @Test
    void accept_returns_400_when_path_feedbackId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/not-uuid/accept")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"%s"}
                    """.formatted(SUGGESTION_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(acceptService, never()).accept(any());
    }

    @Test
    void accept_returns_400_when_suggestionId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/accept", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"suggestionId":"not-uuid"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(acceptService, never()).accept(any());
    }

    // ============================================================
    // POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject
    // ============================================================

    @Test
    void reject_returns_204_with_no_content() throws Exception {
        mockValidAuth();
        when(rejectService.reject(any())).thenReturn(suggestedResult());  // 결과는 폐기

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/reject", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"prefer my own"}
                    """))
            .andExpect(status().isNoContent());

        ArgumentCaptor<RejectSentenceFeedbackCommand> captor =
            ArgumentCaptor.forClass(RejectSentenceFeedbackCommand.class);
        verify(rejectService).reject(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().feedbackId()).isEqualTo(FEEDBACK_ID);
        assertThat(captor.getValue().reasonOrNull()).isEqualTo("prefer my own");
    }

    @Test
    void reject_accepts_empty_body_with_null_reason() throws Exception {
        mockValidAuth();
        when(rejectService.reject(any())).thenReturn(suggestedResult());

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/reject", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNoContent());

        ArgumentCaptor<RejectSentenceFeedbackCommand> captor =
            ArgumentCaptor.forClass(RejectSentenceFeedbackCommand.class);
        verify(rejectService).reject(captor.capture());
        assertThat(captor.getValue().reasonOrNull()).isNull();
    }

    @Test
    void reject_returns_404_when_NotFound() throws Exception {
        mockValidAuth();
        when(rejectService.reject(any()))
            .thenThrow(new SentenceFeedbackNotFoundException("not found"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/reject", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_NOT_FOUND"));
    }

    @Test
    void reject_returns_409_when_already_final() throws Exception {
        mockValidAuth();
        when(rejectService.reject(any()))
            .thenThrow(new SentenceFeedbackInvalidTransitionException("already final"));

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/reject", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_INVALID_TRANSITION"));
    }

    @Test
    void reject_returns_400_when_reason_exceeds_4000_char_bean_validation() throws Exception {
        // test-reviewer M2 — Bean Validation 1차 거부 (도메인 1000 cp 검증 도달 전)
        mockValidAuth();
        String tooLong = "x".repeat(4001);

        mockMvc.perform(post("/api/v1/diaries/sentence-feedback/{id}/reject", FEEDBACK_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"%s"}
                    """.formatted(tooLong)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SENTENCE_FEEDBACK_VALIDATION_FAILED"));
        verify(rejectService, never()).reject(any());
    }
}
