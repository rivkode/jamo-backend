package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.UpdateDiaryCommand;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryContent;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일기 수정 use case (PRD 0526_flutter.md §2.4 / Slice 3-a).
 *
 * <p>박제: plan §5 S3-a + 사용자 결정 Q-S3a-1 (비작성자 응답 404 IDOR 통일).
 *
 * <p><b>단일 트랜잭션</b>: Diary 조회 (row lock) + update + save + likedByMe 조회 모두 같은 트랜잭션 안.
 * UserSummary gRPC 만 트랜잭션 외부 (read-only 조립용 — GetDiaryService 정합).
 *
 * <p><b>Outbox 미발행 결정 (code-reviewer H3)</b>: 현 시점 {@code DiaryUpdated} contract 가 contracts 모듈에
 * 없고 구독 도메인 (검색 인덱스 / 활동 랭킹 / 알림) 이 부재. 본 service 는 outbox publish 를 의도적으로 생략.
 * <b>위험</b>: 향후 PRIVATE↔PUBLIC 전환 / content 변경을 구독해야 할 도메인이 등장하면 historical replay
 * 가 불가능 (event log 가 비어 있어 backfill 불가). 도입 시 다음 마이그레이션 전략 필요:
 * <ol>
 *   <li>1) {@code DiaryUpdated} contract 추가 PR 머지.</li>
 *   <li>2) 본 service 에 outbox publish 추가 (단일 트랜잭션).</li>
 *   <li>3) 구독 도메인은 도입 시점 이후 변경분만 받음을 명시 (backfill 정책 박제 필요).</li>
 * </ol>
 *
 * <p>흐름:
 * <ol>
 *   <li>Diary 조회 ({@code findByIdForUpdate} — 동시 like/comment 카운터 갱신과 직렬화) → 부재 시
 *       {@link DiaryNotFoundException} (404).</li>
 *   <li>VO 생성 ({@code DiaryContent} / {@code ImageUrls} / {@code Tags}) — invariant 위반 시 도메인 예외 (400 매핑).</li>
 *   <li>{@link Diary#update} 호출 — 작성자 검증 invariant. 비작성자 → {@link DiaryAccessDeniedException}
 *       (presentation 404 통일).</li>
 *   <li>save → 트랜잭션 외부에서 UserSummary 조립 후 응답.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class UpdateDiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryLikeRepository diaryLikeRepository;
    private final UserSummaryPort userSummaryPort;
    private final TransactionTemplate transactionTemplate;

    public DiaryView update(UpdateDiaryCommand command) {
        DiaryId diaryId = DiaryId.of(command.diaryId());

        // 트랜잭션 안에서 findByIdForUpdate → ownership 검증 → VO 생성 순.
        // VO 생성을 트랜잭션 밖에 두면 비작성자의 invalid body 가 InvalidDiaryContentException (400) 으로
        // 먼저 응답되어 IDOR 누출 (code-reviewer H1). likedByMe 조회도 트랜잭션 안으로 — findByIdForUpdate
        // 의 row lock 과 같은 스냅샷에서 일관성 (code-reviewer H2 / GetDiaryService 패턴 정합).
        ReadResult result = transactionTemplate.execute(status -> {
            Diary diary = diaryRepository.findByIdForUpdate(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(
                    "diary not found: " + diaryId.asString()));
            // ownership 검증을 VO 생성 전에 — Aggregate.update 의 invariant 와 동일. invalid body 라도
            // 비작성자는 404 IDOR 로 통일 응답.
            if (!diary.isOwnedBy(command.editorId())) {
                throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
            }
            DiaryContent newContent = new DiaryContent(command.content());
            ImageUrls newImages = new ImageUrls(command.images());
            Tags newTags = Tags.ofStrings(command.tags());
            diary.update(newContent, newImages, newTags, command.visibility(), command.editorId());
            diaryRepository.save(diary);
            boolean likedByMe = diaryLikeRepository.existsByDiaryIdAndUserId(diaryId, command.editorId());
            return new ReadResult(diary, likedByMe);
        });

        String displayName = UserSummaryView.displayNameOrUnknown(
            userSummaryPort.get(command.editorId()));

        return DiaryView.from(result.diary(), displayName, result.likedByMe());
    }

    /** 트랜잭션 안에서 조립된 결과 — display name 조회는 트랜잭션 외부에서 수행. */
    private record ReadResult(Diary diary, boolean likedByMe) {
    }
}
