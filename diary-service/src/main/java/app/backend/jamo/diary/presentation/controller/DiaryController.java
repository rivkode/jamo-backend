package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.cursor.DiaryFeedCursorCodec;
import app.backend.jamo.diary.application.dto.diary.CreateDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DeleteDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.GetDiaryQuery;
import app.backend.jamo.diary.application.dto.diary.ListMyFeedQuery;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.service.diary.CreateDiaryService;
import app.backend.jamo.diary.application.service.diary.DeleteDiaryService;
import app.backend.jamo.diary.application.service.diary.GetDiaryService;
import app.backend.jamo.diary.application.service.diary.ListMyFeedService;
import app.backend.jamo.diary.application.service.diary.ListPublicFeedService;
import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import app.backend.jamo.diary.presentation.dto.CreateDiaryRequest;
import app.backend.jamo.diary.presentation.dto.DiaryResponse;
import app.backend.jamo.diary.presentation.dto.FeedResponse;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * diary core HTTP API (PRD diary/{create,get,listFeed,listMyFeed,delete}.md).
 *
 * <p>5 endpoint:
 * <ul>
 *   <li>POST /api/v1/diaries — 일기 작성 (201 + DiaryResponse 11 필드)</li>
 *   <li>GET /api/v1/diaries/{diaryId} — 단건 조회 (200 + DiaryResponse, 비공개+비작성자 → 404 IDOR)</li>
 *   <li>GET /api/v1/diaries/feed — 공개 피드 (200 + FeedResponse, sort=recent|popular)</li>
 *   <li>GET /api/v1/diaries/me — 본인 피드 (200 + FeedResponse, RECENT only)</li>
 *   <li>DELETE /api/v1/diaries/{diaryId} — 작성자 삭제 (204, 비작성자 → 404 IDOR)</li>
 * </ul>
 *
 * <p>모두 인증 필수 ({@code @LoginUser}). path UUID 는 문자열로 받아 Controller 에서 변환 — 변환 실패 (invalid
 * UUID) 시 IAE → DiaryExceptionHandler 가 400 매핑. cursor 디코딩 / Tag VO 조립 / Visibility default 변환은
 * Controller 책임 (박제 §3 / §7).
 *
 * <p>{@code @Validated} 는 {@code @RequestParam} 단의 {@code @Min/@Max} 활성화용 — body 는
 * {@code @Valid} 로 처리.
 */
@RestController
@RequestMapping("/api/v1/diaries")
@SecurityRequirement(name = "BearerJwt")
@Validated
@RequiredArgsConstructor
public class DiaryController {

    private final CreateDiaryService createDiaryService;
    private final GetDiaryService getDiaryService;
    private final ListPublicFeedService listPublicFeedService;
    private final ListMyFeedService listMyFeedService;
    private final DeleteDiaryService deleteDiaryService;

    @PostMapping
    public ResponseEntity<DiaryResponse> create(
        @LoginUser AuthenticatedUser auth,
        @Valid @RequestBody CreateDiaryRequest body
    ) {
        Visibility visibility = body.visibility() == null
            ? Visibility.PUBLIC
            : Visibility.valueOf(body.visibility());
        DiaryView view = createDiaryService.create(new CreateDiaryCommand(
            auth.userId(),
            body.content(),
            body.images(),
            body.tags(),
            visibility
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(DiaryResponse.from(view));
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> get(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        DiaryView view = getDiaryService.get(new GetDiaryQuery(diaryUuid, auth.userId()));
        return ResponseEntity.ok(DiaryResponse.from(view));
    }

    @GetMapping("/feed")
    public ResponseEntity<FeedResponse> listFeed(
        @LoginUser AuthenticatedUser auth,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(name = "sort", required = false) String sort,
        @RequestParam(name = "tag", required = false) String tag
    ) {
        DiaryFeedSort sortValue = sort == null
            ? DiaryFeedSort.defaultSort()
            : DiaryFeedSort.valueOf(sort.toUpperCase());

        Optional<RecentFeedCursor> recentCursor = (sortValue == DiaryFeedSort.RECENT && cursor != null)
            ? Optional.of(DiaryFeedCursorCodec.decodeRecent(cursor))
            : Optional.empty();
        Optional<PopularFeedCursor> popularCursor = (sortValue == DiaryFeedSort.POPULAR && cursor != null)
            ? Optional.of(DiaryFeedCursorCodec.decodePopular(cursor))
            : Optional.empty();

        Optional<Tag> tagVo = (tag == null || tag.isBlank())
            ? Optional.empty()
            : Optional.of(new Tag(tag));

        FeedView view = listPublicFeedService.listPublicFeed(new ListPublicFeedQuery(
            auth.userId(),
            tagVo,
            sortValue,
            recentCursor,
            popularCursor,
            size
        ));
        return ResponseEntity.ok(FeedResponse.from(view));
    }

    @GetMapping("/me")
    public ResponseEntity<FeedResponse> listMyFeed(
        @LoginUser AuthenticatedUser auth,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Optional<RecentFeedCursor> recentCursor = cursor == null
            ? Optional.empty()
            : Optional.of(DiaryFeedCursorCodec.decodeRecent(cursor));

        FeedView view = listMyFeedService.listMyFeed(new ListMyFeedQuery(
            auth.userId(),
            recentCursor,
            size
        ));
        return ResponseEntity.ok(FeedResponse.from(view));
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> delete(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        deleteDiaryService.delete(new DeleteDiaryCommand(diaryUuid, auth.userId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
