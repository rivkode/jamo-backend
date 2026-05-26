package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeCommand;
import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeView;
import app.backend.jamo.diary.application.service.comment.ToggleCommentLikeService;
import app.backend.jamo.diary.presentation.dto.ToggleCommentLikeRequest;
import app.backend.jamo.diary.presentation.dto.ToggleCommentLikeResponse;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * comment like HTTP API (PRD 0526_flutter.md §3.3).
 *
 * <p>1 endpoint:
 * <ul>
 *   <li>POST /api/v1/comments/{commentId}/like — 좋아요 토글 (200 + ToggleCommentLikeResponse)</li>
 * </ul>
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 (boolean 명시 멱등 — diary 좋아요 정합).
 *
 * <p>{@link CommentController} 와 분리한 이유: PRD 의 base path 분리 ({@code /comments/*}) + 좋아요 도메인
 * (CommentLike Aggregate) 의 Application Service 가 별도 — 책임 응집 ({@link DiaryLikeController} 정합).
 *
 * <p>{@code @Validated} 미적용 — query 파라미터 없음. body validation 은 메서드 인자 {@code @Valid} 로 충분.
 */
@RestController
@SecurityRequirement(name = "BearerJwt")
@RequiredArgsConstructor
public class CommentLikeController {

    private final ToggleCommentLikeService toggleCommentLikeService;

    @PostMapping("/api/v1/comments/{commentId}/like")
    public ResponseEntity<ToggleCommentLikeResponse> toggle(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("commentId") String commentId,
        @Valid @RequestBody ToggleCommentLikeRequest body
    ) {
        UUID commentUuid = UUID.fromString(commentId);
        ToggleCommentLikeView view = toggleCommentLikeService.toggle(new ToggleCommentLikeCommand(
            commentUuid,
            auth.userId(),
            body.liked()
        ));
        return ResponseEntity.ok(ToggleCommentLikeResponse.from(view));
    }
}
