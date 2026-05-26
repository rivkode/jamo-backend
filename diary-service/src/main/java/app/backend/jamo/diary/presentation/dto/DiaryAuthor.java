package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Diary 응답에 임베드되는 작성자 요약 (PRD 0526_flutter.md §2 Diary 스키마, Slice 2 alias).
 *
 * <p>{@code username} 은 백엔드 도메인 displayName 의 외부 명세 정합 alias. {@code avatarUrl} 은 현 시점 미구현
 * 도메인이라 항상 {@code null}.
 *
 * <p><b>null 직렬화 정책</b> ({@code CommentAuthor} 정합): {@code avatarUrl: null} 은 키 자체를 응답에
 * 포함한다 (Spring Boot Jackson 기본 {@code Include.ALWAYS}). PRD §2 의 {@code "avatarUrl": null} 명시
 * 노출과 정합.
 *
 * @param userId    작성자 UUID
 * @param username  displayName 동의어 (PRD 명세 필드명)
 * @param avatarUrl 미구현 — 항상 null
 */
public record DiaryAuthor(UUID userId, String username, String avatarUrl) {

    public DiaryAuthor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
    }
}
