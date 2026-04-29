-- D-a-5-impl-batch — batch query 인덱스 보강 (security-reviewer M1).
--
-- 이전 마이그레이션 V5 의 sentence_feedback 인덱스: (user_id, status), diary_id, expires_at.
-- decided_at 인덱스 부재 → cleanup batch (`status IN (...) AND decided_at < cutoff`) 가 풀 스캔.
-- FOR UPDATE SKIP LOCKED 가 모든 row 에 next-key lock → 운영 트래픽과 동시 발생 시 자해성 DoS.
--
-- processed_event 의 processed_at 도 동일 (cleanup batch 가 `processed_at < cutoff` 풀 스캔).
-- outbox_event 의 idx_outbox_event_published_at (published_at, id) 는 V5 에 이미 있음 — OK.

-- (status, decided_at) 복합 — IN-list 4종 (ACCEPTED/REJECTED/EXPIRED/FAILED) selectivity 보강.
-- batch query 의 ORDER BY decided_at ASC 도 인덱스로 처리 가능.
ALTER TABLE sentence_feedback
    ADD INDEX idx_sentence_feedback_status_decided_at (status, decided_at);

ALTER TABLE processed_event
    ADD INDEX idx_processed_event_processed_at (processed_at);
