package app.backend.jamo.identity.domain.model.profile;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Profile aggregate — 사용자 외형 정보 (bio / avatarUrl / locale).
 *
 * <p><b>shared identifier 패턴</b> (decisions/identity/profile-prd-evaluation.md §결과및영향 #Domain,
 * IDDD Ch.10): 식별자는 {@link UserId} 와 *같은 값*. 별도 ProfileId VO 미신설. {@code Profile.id == User.id}
 * 가 도메인 invariant — 1:1 매핑.
 *
 * <p><b>displayName 미보유</b>: SoT 는 User aggregate (`User.displayName`, `User.rename`).
 * Profile 은 외형 책임만. /me PATCH 의 displayName 변경은 Application 계층에서 User aggregate 의
 * `rename` 을 호출 — cross-aggregate 트랜잭션 (같은 BC 내 강결합 1:1 예외, profile-prd-evaluation §결정 #4).
 *
 * <p>외형 필드 nullable 정책: `bio` / `avatarUrl` 는 미설정 표현으로 null 허용. `locale` 은 non-null
 * (기본값 {@link Locale#DEFAULT} = "ko"). PATCH 의 변경 의미는 부분 변경 메서드 ({@link #changeBio} /
 * {@link #changeAvatarUrl} / {@link #changeLocale}) 로 표현 — Application 계층이 비-null 필드만 호출하면
 * 명시적 unset 은 별도 메서드 ({@link #unsetBio} / {@link #unsetAvatarUrl}) 로 표현.
 */
public final class Profile {

    private final UserId id;
    private Bio bio;
    private AvatarUrl avatarUrl;
    private Locale locale;
    private final Instant createdAt;
    private Instant updatedAt;

    private Profile(UserId id, Bio bio, AvatarUrl avatarUrl, Locale locale,
                    Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.locale = Objects.requireNonNull(locale, "locale");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 신규 Profile 생성 — 회원가입 시 호출 가정 (Profile 생성 시점은 Phase 6-b-b 슬라이스에서 결정).
     * bio / avatarUrl 은 null (미설정), locale 은 기본값 "ko".
     */
    public static Profile create(UserId id, Instant now) {
        return new Profile(id, null, null, Locale.DEFAULT, now, now);
    }

    /**
     * Repository 복원용 — JpaEntity → Domain 변환 시 호출.
     */
    public static Profile restore(UserId id, Bio bio, AvatarUrl avatarUrl, Locale locale,
                                  Instant createdAt, Instant updatedAt) {
        return new Profile(id, bio, avatarUrl, locale, createdAt, updatedAt);
    }

    public void changeBio(Bio bio, Instant now) {
        this.bio = Objects.requireNonNull(bio, "bio");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void unsetBio(Instant now) {
        this.bio = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void changeAvatarUrl(AvatarUrl avatarUrl, Instant now) {
        this.avatarUrl = Objects.requireNonNull(avatarUrl, "avatarUrl");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void unsetAvatarUrl(Instant now) {
        this.avatarUrl = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void changeLocale(Locale locale, Instant now) {
        this.locale = Objects.requireNonNull(locale, "locale");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UserId id() { return id; }
    public Optional<Bio> bio() { return Optional.ofNullable(bio); }
    public Optional<AvatarUrl> avatarUrl() { return Optional.ofNullable(avatarUrl); }
    public Locale locale() { return locale; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
