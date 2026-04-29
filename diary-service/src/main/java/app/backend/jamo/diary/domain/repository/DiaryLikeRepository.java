package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DiaryLike Aggregate Repository port.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 (좋아요 멱등) / §9 (DiaryDeleted cascade) /
 * §11 (회원 탈퇴 cascade).
 *
 * <p><b>유니크 제약</b>: {@code (diary_id, user_id)} 동시 INSERT 차단. 멱등 UPSERT/DELETE 는 구현체에서
 * 동시성 안전 보장 (DB unique violation 또는 SELECT FOR UPDATE).
 *
 * <p><b>cleanup 메서드 ({@link #deleteAllByDiaryId} / {@link #deleteAllByUserId})</b>: Saga 구독자
 * ({@code DiaryLikeOnDiaryDeletedListener}, 회원 탈퇴 처리기) 만 호출. sentence-feedback 정합.
 */
public interface DiaryLikeRepository {

    void save(DiaryLike like);

    Optional<DiaryLike> findByDiaryIdAndUserId(DiaryId diaryId, UUID userId);

    boolean existsByDiaryIdAndUserId(DiaryId diaryId, UUID userId);

    /** 멱등 — 존재하지 않는 row 삭제 시 no-op. */
    void deleteByDiaryIdAndUserId(DiaryId diaryId, UUID userId);

    long countByDiaryId(DiaryId diaryId);

    /**
     * 피드 응답의 {@code likedByMe} 일괄 조회 (N+1 회피).
     *
     * <p>입력 / 출력 모두 {@link Set} — 입력 중복은 자동 dedup, 출력은 입력의 부분집합. 호출자가 한 페이지 (size <= 100)
     * 기준 사용 가정 — 100 초과 시 SQL {@code IN} 절 효율 저하 또는 DB 한계.
     *
     * @param userId   호출자
     * @param diaryIds 한 페이지의 diaryId 묶음 (size <= 100)
     * @return 호출자가 좋아요 누른 diaryId 의 부분집합
     */
    Set<DiaryId> findDiaryIdsLikedByUser(UUID userId, Set<DiaryId> diaryIds);

    /**
     * DiaryDeleted Saga cascade — {@code DiaryLikeOnDiaryDeletedListener} 가 호출. 박제 §9. 일반 Application
     * Service 가 호출하지 않는다.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByDiaryId(DiaryId diaryId);

    /**
     * 회원 탈퇴 Saga cascade — 사용자 소유 모든 좋아요 row hard-delete.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByUserId(UUID userId);
}
