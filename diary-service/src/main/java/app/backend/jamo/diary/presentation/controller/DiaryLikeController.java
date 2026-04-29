package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeCommand;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import app.backend.jamo.diary.application.service.diary.ToggleDiaryLikeService;
import app.backend.jamo.diary.presentation.dto.ToggleDiaryLikeRequest;
import app.backend.jamo.diary.presentation.dto.ToggleDiaryLikeResponse;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * diary like HTTP API (PRD diary/toggleLike.md).
 *
 * <p>1 endpoint:
 * <ul>
 *   <li>POST /api/v1/diaries/{diaryId}/like — 좋아요 토글 (200 + ToggleDiaryLikeResponse 즉시 likeCount)</li>
 * </ul>
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 (명시적 boolean 멱등 — comment 정합).
 *
 * <p>인증 필수 ({@code @LoginUser}). path UUID 변환 실패 → IAE → DiaryExceptionHandler 400. 비공개+비작성자
 * → 404 (자기 비공개 일기 좋아요는 허용 — 박제 §8 일관성).
 *
 * <p>{@link DiaryController} 와 컨트롤러를 분리한 이유: PRD {@code toggleLike.md} 의 controller 분리 명시 +
 * 좋아요 도메인 (DiaryLike Aggregate) 의 Application Service 가 별도 — 책임 응집.
 *
 * <p>{@code @Validated} 미적용 — 본 Controller 는 query 파라미터 ({@code @RequestParam}) 가 없어
 * {@code ConstraintViolationException} 트리거 대상이 없다. body validation 은 메서드 인자의
 * {@code @Valid} 로 충분 (DiaryController 와 의도적으로 다른 결정).
 */
@RestController
@RequestMapping("/api/v1/diaries")
@SecurityRequirement(name = "BearerJwt")
@RequiredArgsConstructor
public class DiaryLikeController {

    private final ToggleDiaryLikeService toggleDiaryLikeService;

    @PostMapping("/{diaryId}/like")
    public ResponseEntity<ToggleDiaryLikeResponse> toggle(
        @LoginUser AuthenticatedUser auth,
        @PathVariable("diaryId") String diaryId,
        @Valid @RequestBody ToggleDiaryLikeRequest body
    ) {
        UUID diaryUuid = UUID.fromString(diaryId);
        ToggleDiaryLikeView view = toggleDiaryLikeService.toggle(new ToggleDiaryLikeCommand(
            diaryUuid,
            auth.userId(),
            body.liked()
        ));
        return ResponseEntity.ok(ToggleDiaryLikeResponse.from(view));
    }
}
