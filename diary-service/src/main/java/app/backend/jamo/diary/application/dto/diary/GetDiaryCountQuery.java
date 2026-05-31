package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 작성자별 일기 수 조회 use case 입력 (Slice 3-b / DiaryQueryService.GetDiaryCount).
 *
 * @param authorId       집계 대상 작성자
 * @param includePrivate true = 전체 (본인 프로필) / false = PUBLIC 만 (타인 프로필, IDOR 차단).
 *                       호출 측(identity-service) 이 viewer == target 으로 결정해 전달.
 */
public record GetDiaryCountQuery(UUID authorId, boolean includePrivate) {

    public GetDiaryCountQuery {
        Objects.requireNonNull(authorId, "authorId");
    }
}
