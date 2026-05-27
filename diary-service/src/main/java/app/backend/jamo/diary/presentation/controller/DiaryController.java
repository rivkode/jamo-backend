package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.diary.CreateDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DeleteDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.GetDiaryQuery;
import app.backend.jamo.diary.application.dto.diary.ListMyFeedQuery;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.dto.diary.UpdateDiaryCommand;
import app.backend.jamo.diary.application.service.diary.CreateDiaryService;
import app.backend.jamo.diary.application.service.diary.DeleteDiaryService;
import app.backend.jamo.diary.application.service.diary.GetDiaryService;
import app.backend.jamo.diary.application.service.diary.ListMyFeedService;
import app.backend.jamo.diary.application.service.diary.ListPublicFeedService;
import app.backend.jamo.diary.application.service.diary.UpdateDiaryService;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.presentation.dto.CreateDiaryRequest;
import app.backend.jamo.diary.presentation.dto.DiaryResponse;
import app.backend.jamo.diary.presentation.dto.FeedResponse;
import app.backend.jamo.diary.presentation.dto.UpdateDiaryRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * diary core HTTP API (PRD diary/{create,get,listFeed,listMyFeed,delete}.md).
 *
 * <p>6 endpoint:
 * <ul>
 *   <li>POST /api/v1/diaries — 일기 작성 (201 + DiaryResponse 11 필드)</li>
 *   <li>GET /api/v1/diaries/{diaryId} — 단건 조회 (200 + DiaryResponse, 비공개+비작성자 → 404 IDOR)</li>
 *   <li>GET /api/v1/diaries/feed — 공개 피드 (200 + FeedResponse, sort=recent|popular)</li>
 *   <li>GET /api/v1/diaries/me — 본인 피드 (200 + FeedResponse, RECENT only)</li>
 *   <li>PUT /api/v1/diaries/{diaryId} — 작성자 수정 (200 + DiaryResponse, 비작성자 → 404 IDOR — Slice 3-a)</li>
 *   <li>DELETE /api/v1/diaries/{diaryId} — 작성자 삭제 (204, 비작성자 → 404 IDOR)</li>
 * </ul>
 *
 * <p>모두 인증 필수 ({@code @LoginUser}). path UUID 는 문자열로 받아 Controller 에서 변환 — 변환 실패 (invalid
 * UUID) 시 IAE → DiaryExceptionHandler 가 400 매핑.
 *
 * <p><b>책임 재배치 (cleanup PR — code-reviewer M1/M5)</b>: tag VO / DiaryFeedSort enum / cursor codec 의
 * 직접 조립은 Application Service 책임으로 이전. Controller 는 raw String tag/sort/cursor 를 그대로 Query
 * 에 넣어 전달만. visibility default PUBLIC 변환은 Controller 책임 유지 (박제 §3 — Application Command 는
 * 명시 값 강제).
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
    private final UpdateDiaryService updateDiaryService;

    @PostMapping
    public ResponseEntity<DiaryResponse> create(
        @LoginUser AuthenticatedUser auth,
        @Valid @RequestBody CreateDiaryRequest body
    ) {
        Visibility visibility = resolveVisibility(body.visibility());
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
        FeedView view = listPublicFeedService.listPublicFeed(new ListPublicFeedQuery(
            auth.userId(), tag, sort, cursor, size
        ));
        return ResponseEntity.ok(FeedResponse.from(view));
    }

    @GetMapping("/me")
    public ResponseEntity<FeedResponse> listMyFeed(
        @LoginUser AuthenticatedUser auth,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        FeedView view = listMyFeedService.listMyFeed(new ListMyFeedQuery(
            auth.userId(), cursor, size
        ));
        return ResponseEntity.ok(FeedResponse.from(view));
    }

    @PutMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> update(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId,
        @Valid @RequestBody UpdateDiaryRequest body
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        Visibility visibility = resolveVisibility(body.visibility());
        DiaryView view = updateDiaryService.update(new UpdateDiaryCommand(
            diaryUuid,
            auth.userId(),
            body.content(),
            body.images(),
            body.tags(),
            visibility
        ));
        return ResponseEntity.ok(DiaryResponse.from(view));
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

    /**
     * raw String visibility → enum 변환. null/blank → PUBLIC default. invalid 값 → IAE (ExceptionHandler 400).
     * create / update 양쪽에서 동일 정책. Bean Validation {@code @Pattern} 이 이미 invalid 차단하므로
     * {@code Visibility.valueOf} IAE 는 실질 도달 불가 경로이나 deep-defense.
     */
    private static Visibility resolveVisibility(String raw) {
        return raw == null ? Visibility.PUBLIC : Visibility.valueOf(raw);
    }
}
