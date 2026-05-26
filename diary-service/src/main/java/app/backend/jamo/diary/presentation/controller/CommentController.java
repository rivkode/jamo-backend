package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.comment.CommentListView;
import app.backend.jamo.diary.application.dto.comment.CommentView;
import app.backend.jamo.diary.application.dto.comment.CreateCommentCommand;
import app.backend.jamo.diary.application.dto.comment.DeleteCommentCommand;
import app.backend.jamo.diary.application.dto.comment.ListCommentsQuery;
import app.backend.jamo.diary.application.service.comment.CreateCommentService;
import app.backend.jamo.diary.application.service.comment.DeleteCommentService;
import app.backend.jamo.diary.application.service.comment.ListCommentsService;
import app.backend.jamo.diary.presentation.dto.CommentListResponse;
import app.backend.jamo.diary.presentation.dto.CommentResponse;
import app.backend.jamo.diary.presentation.dto.CreateCommentRequest;
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

import java.util.UUID;

/**
 * comment HTTP API (PRD 0526_flutter.md §3.1 / §3.2 / §3.4).
 *
 * <p>3 endpoint:
 * <ul>
 *   <li>POST /api/v1/diaries/{diaryId}/comments — 댓글 작성 (201 + CommentResponse)</li>
 *   <li>GET /api/v1/diaries/{diaryId}/comments?cursor&size=20 — 목록 조회 (cursor pagination)</li>
 *   <li>DELETE /api/v1/comments/{commentId} — 작성자 삭제 (204, 비작성자 → 404 IDOR)</li>
 * </ul>
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §4 (404 IDOR 통일) / §6 (cursor opaque, size default 20).
 *
 * <p>모두 인증 필수 ({@code @LoginUser}). path UUID 는 문자열로 받아 Controller 에서 변환 — 변환 실패 (invalid
 * UUID) 시 IAE → {@code CommentExceptionHandler} 가 400 매핑.
 *
 * <p>좋아요 토글은 {@link CommentLikeController} 로 분리 — {@link DiaryController} + {@link DiaryLikeController}
 * 패턴 정합.
 *
 * <p>{@code @Validated} 는 {@code @RequestParam} 단의 {@code @Min/@Max} 활성화용 — body 는 {@code @Valid} 로 처리.
 *
 * <p><b>full path 의도</b> (code-reviewer M3): {@link DiaryController} 는 {@code @RequestMapping("/api/v1/diaries")}
 * base path 패턴을 쓰지만, 본 controller 는 두 base path ({@code /api/v1/diaries/{id}/comments},
 * {@code /api/v1/comments/{id}}) 에 걸쳐 있어 클래스 레벨 prefix 가 불가. 메서드 레벨에 full path 명시 — grep 친화성 +
 * 의도 명확성 우선.
 */
@RestController
@SecurityRequirement(name = "BearerJwt")
@Validated
@RequiredArgsConstructor
public class CommentController {

    private final CreateCommentService createCommentService;
    private final ListCommentsService listCommentsService;
    private final DeleteCommentService deleteCommentService;

    @PostMapping("/api/v1/diaries/{diaryId}/comments")
    public ResponseEntity<CommentResponse> create(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId,
        @Valid @RequestBody CreateCommentRequest body
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        UUID parentUuid = (body.parentCommentId() == null || body.parentCommentId().isBlank())
            ? null
            : UUID.fromString(body.parentCommentId());
        CommentView view = createCommentService.create(new CreateCommentCommand(
            diaryUuid,
            auth.userId(),
            body.text(),
            parentUuid
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(view));
    }

    @GetMapping("/api/v1/diaries/{diaryId}/comments")
    public ResponseEntity<CommentListResponse> list(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        CommentListView view = listCommentsService.list(new ListCommentsQuery(
            diaryUuid,
            auth.userId(),
            cursor,
            size
        ));
        return ResponseEntity.ok(CommentListResponse.from(view));
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<Void> delete(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("commentId") String commentId
    ) {
        UUID commentUuid = UUID.fromString(commentId);
        deleteCommentService.delete(new DeleteCommentCommand(commentUuid, auth.userId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
