package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpireSentenceFeedbackServiceTest {

    private ExpireSentenceFeedbackTx tx;
    private ExpireSentenceFeedbackService service;

    @BeforeEach
    void setUp() {
        tx = mock(ExpireSentenceFeedbackTx.class);
        service = new ExpireSentenceFeedbackService(tx);
    }

    @Test
    void empty_chunk_returns_zero_stats_no_expireOne_call() {
        when(tx.findExpirableIds(100)).thenReturn(List.of());

        ExpireSentenceFeedbackService.Result result = service.run(100);

        assertThat(result.candidates()).isZero();
        assertThat(result.expired()).isZero();
        assertThat(result.skipped()).isZero();
        verify(tx, never()).expireOne(any());
    }

    @Test
    void single_candidate_expired() {
        SentenceFeedbackId id = SentenceFeedbackId.newId();
        when(tx.findExpirableIds(100)).thenReturn(List.of(id));
        doNothing().when(tx).expireOne(id);

        ExpireSentenceFeedbackService.Result result = service.run(100);

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        verify(tx).expireOne(id);
    }

    @Test
    void chunk_of_three_candidates_all_expired_in_order() {
        SentenceFeedbackId id1 = SentenceFeedbackId.newId();
        SentenceFeedbackId id2 = SentenceFeedbackId.newId();
        SentenceFeedbackId id3 = SentenceFeedbackId.newId();
        when(tx.findExpirableIds(100)).thenReturn(List.of(id1, id2, id3));

        ExpireSentenceFeedbackService.Result result = service.run(100);

        assertThat(result.candidates()).isEqualTo(3);
        assertThat(result.expired()).isEqualTo(3);
        assertThat(result.skipped()).isZero();
        // test-reviewer M5 — 호출 순서 보장 (find 결과의 expires_at ASC order 와 정합)
        InOrder order = inOrder(tx);
        order.verify(tx).findExpirableIds(100);
        order.verify(tx).expireOne(id1);
        order.verify(tx).expireOne(id2);
        order.verify(tx).expireOne(id3);
    }

    @Test
    void race_condition_other_instance_already_expired_skipped_but_other_rows_continue() {
        SentenceFeedbackId raceId = SentenceFeedbackId.newId();
        SentenceFeedbackId healthyId = SentenceFeedbackId.newId();
        when(tx.findExpirableIds(100)).thenReturn(List.of(raceId, healthyId));
        doThrow(new SentenceFeedbackInvalidTransitionException("already accepted"))
            .when(tx).expireOne(raceId);
        doNothing().when(tx).expireOne(healthyId);

        ExpireSentenceFeedbackService.Result result = service.run(100);

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        // healthyId 는 race 후에도 처리됨 (한 row 실패가 다음 row 차단 X)
        verify(tx).expireOne(healthyId);
    }

    @Test
    void cleanup_batch_already_deleted_row_skipped() {
        SentenceFeedbackId deletedId = SentenceFeedbackId.newId();
        SentenceFeedbackId healthyId = SentenceFeedbackId.newId();
        when(tx.findExpirableIds(100)).thenReturn(List.of(deletedId, healthyId));
        doThrow(new SentenceFeedbackNotFoundException("not found"))
            .when(tx).expireOne(deletedId);
        doNothing().when(tx).expireOne(healthyId);

        ExpireSentenceFeedbackService.Result result = service.run(100);

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void chunk_size_passed_through_to_tx() {
        when(tx.findExpirableIds(50)).thenReturn(List.of());

        service.run(50);

        verify(tx).findExpirableIds(eq(50));
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
