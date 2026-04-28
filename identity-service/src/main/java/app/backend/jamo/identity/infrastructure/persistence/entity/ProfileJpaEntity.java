package app.backend.jamo.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * profiles 테이블 매핑.
 *
 * <p><b>shared identifier</b> — PK 가 {@code user_id} (외래 ID, BINARY(16)). FK constraint 미사용
 * (ADR-0005). {@code display_name} 컬럼 미생성 (User SoT,
 * decisions/identity/profile-app-infra-decisions.md §Flyway V4).
 *
 * <p><b>Optimistic locking</b> — {@code @Version} 으로 동시 PATCH (브라우저 두 탭 등) 시 last-write-wins
 * 회피. {@code UserJpaEntity} 와 정합 (code review H1).
 */
@Entity
@Getter
@Table(name = "profiles")
public class ProfileJpaEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "bio", length = 200)
    private String bio;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ProfileJpaEntity() {
    }

    public ProfileJpaEntity(UUID userId, String bio, String avatarUrl, String locale,
                            Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.locale = locale;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // setter 는 명시 유지 — Mapper 의 mergeInto 패턴에서 호출.
    public void setBio(String bio) { this.bio = bio; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setLocale(String locale) { this.locale = locale; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
