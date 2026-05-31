package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.diary.application.service.sentencefeedback.CleanupSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.ExpireSentenceFeedbackTx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회귀 가드 — {@code FOR UPDATE SKIP LOCKED} batch 조회를 실행하는 모든 entry 메서드의 트랜잭션이
 * active + writable 임을 강제.
 *
 * <p><b>버그</b>: batch entry 메서드가 {@code @Transactional(readOnly = true)} 였을 때, MySQL
 * Connector/J 가 read-only 를 서버 세션({@code SET TRANSACTION READ ONLY})에 전파 → 그 안의
 * locking read({@code SELECT ... FOR UPDATE SKIP LOCKED})가 <b>error 1792 (SQLState 25006)</b>
 * "Cannot execute statement in a READ ONLY transaction" 으로 매 polling 사이클 실패.
 *
 * <p>이 결함은 트랜잭션 <i>속성</i>(메타데이터) 문제라 Mockito 단위 테스트(repository mock)나
 * {@code @DataJpaTest} 슬라이스(테스트 자체가 writable TX 로 감싸 inner readOnly 가 무시됨)로는 구조적으로
 * 잡히지 않는다. 따라서 어노테이션 계약(readOnly=false + active 전파)을 직접 고정한다.
 *
 * <p>diary-service 내 {@code for update skip locked} native 쿼리는 3개이며, 호출 entry 메서드 3개를 모두
 * 커버한다 — expire / outbox / cleanup.
 */
class BatchForUpdateReadOnlyContractTest {

    @Test
    @DisplayName("Outbox findPendingIds 는 active+writable TX (FOR UPDATE → MySQL 1792 회귀 방지)")
    void outboxFindPendingIds_runs_in_writable_transaction() throws NoSuchMethodException {
        assertWritableTransactional(OutboxPublisherTx.class, "findPendingIds");
    }

    @Test
    @DisplayName("Expire findExpirableIds 는 active+writable TX (FOR UPDATE → MySQL 1792 회귀 방지)")
    void expireFindExpirableIds_runs_in_writable_transaction() throws NoSuchMethodException {
        assertWritableTransactional(ExpireSentenceFeedbackTx.class, "findExpirableIds", int.class);
    }

    @Test
    @DisplayName("Cleanup run 은 active+writable TX (FOR UPDATE → MySQL 1792 회귀 방지)")
    void cleanupRun_runs_in_writable_transaction() throws NoSuchMethodException {
        assertWritableTransactional(CleanupSentenceFeedbackService.class, "run", int.class, int.class);
    }

    private void assertWritableTransactional(Class<?> type, String methodName, Class<?>... paramTypes)
        throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, paramTypes);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx)
            .as("%s#%s 는 @Transactional 이어야 함", type.getSimpleName(), methodName)
            .isNotNull();
        assertThat(tx.readOnly())
            .as("%s#%s 는 FOR UPDATE 를 실행하므로 readOnly=false (writable) 여야 함 — "
                + "readOnly=true 면 MySQL error 1792 로 실패", type.getSimpleName(), methodName)
            .isFalse();
        assertThat(tx.propagation())
            .as("%s#%s 는 active TX 안에서 locking read 를 실행해야 함 — TX 없음/SUPPORTS 로의 회귀 차단",
                type.getSimpleName(), methodName)
            .isEqualTo(Propagation.REQUIRED);
    }
}
