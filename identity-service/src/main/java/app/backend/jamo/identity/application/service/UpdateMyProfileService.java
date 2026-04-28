package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.UpdateMyProfileCommand;
import app.backend.jamo.identity.domain.event.DisplayNameChanged;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.DisplayNameChangeRateLimiter;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 본인 프로필 부분 수정 use case (PRD profile/updateMyProfile.md §3,
 * decisions/identity/profile-prd-evaluation.md §결정 #4 cross-aggregate 트랜잭션,
 * decisions/identity/profile-app-infra-decisions.md §결정 #1/#2/#3).
 *
 * <p><b>cross-aggregate 트랜잭션</b> — User + Profile 두 aggregate 를 한 {@code @Transactional}
 * 안에서 다룸. IDDD Ch.10 *Rule of Thumb 2 'Modify One Aggregate per Transaction'* 의 명시적 예외
 * (같은 BC 내 강결합 1:1 aggregate). 본 예외는 /me PATCH 한 곳 한정 — 다른 use case 는 Saga.
 *
 * <p><b>실패 모드 박제</b> (decisions/identity/profile-app-infra-decisions.md §결정 #1):
 * <ul>
 *   <li>{@code rateLimiter.check} 실패 → {@code DisplayNameChangeTooFrequentException} 던짐, 트랜잭션 진입 X</li>
 *   <li>VO 검증 실패 → {@code IllegalArgumentException} 던짐</li>
 *   <li>{@code DisplayNameChanged} 이벤트는 {@code @TransactionalEventListener(AFTER_COMMIT)} —
 *       RDB rollback 시 Redis 미반영 (정합성 안전). commit 후 Redis 다운 시 빈도 제한 미적용 —
 *       사용자 친화적 실패 모드</li>
 * </ul>
 *
 * <p><b>Lazy + Read 기본값 정합</b>: Profile aggregate 가 부재 시 {@link Profile#create}
 * 으로 자연스럽게 lazy 등록 — 첫 PATCH 가 곧 첫 등록 시점.
 */
@Service
public class UpdateMyProfileService {

    public static final Duration DISPLAY_NAME_CHANGE_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DisplayNameChangeRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UpdateMyProfileService(UserRepository userRepository,
                                  ProfileRepository profileRepository,
                                  DisplayNameChangeRateLimiter rateLimiter,
                                  ApplicationEventPublisher eventPublisher,
                                  Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public MyProfileResult update(UpdateMyProfileCommand command) {
        Objects.requireNonNull(command, "command");
        UserId userId = command.userId();
        Instant now = Instant.now(clock);

        // 1. User aggregate (displayName SoT)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user not found: " + userId.value()));

        if (command.displayName() != null) {
            rateLimiter.check(userId);  // flag 있으면 throws — 트랜잭션 진입 후 즉시 거부
            DisplayName newName = new DisplayName(command.displayName());
            user.rename(newName, now);
            userRepository.save(user);  // displayName 변경 시에만 save (불필요한 UPDATE 회피, code review M3)
            eventPublisher.publishEvent(new DisplayNameChanged(userId, DISPLAY_NAME_CHANGE_TTL));
        }

        // 2. Profile aggregate (lazy create)
        Profile profile = profileRepository.findById(userId)
                .orElseGet(() -> Profile.create(userId, now));

        applyBio(profile, command.bio(), now);
        applyAvatarUrl(profile, command.avatarUrl(), now);
        applyLocale(profile, command.locale(), now);

        Profile savedProfile = profileRepository.save(profile);

        // 3. 응답 합성 — 단일 트랜잭션 합성 (decisions/identity/profile-app-infra-decisions.md §결정 #3)
        return composeResult(user, savedProfile);
    }

    private void applyBio(Profile profile, String bio, Instant now) {
        if (bio == null) {
            return;  // null = 변경 없음
        }
        if (bio.trim().isEmpty()) {
            profile.unsetBio(now);  // 빈 문자열 → null 정규화 (소개 제거)
        } else {
            profile.changeBio(new Bio(bio), now);
        }
    }

    private void applyAvatarUrl(Profile profile, String avatarUrl, Instant now) {
        if (avatarUrl == null) {
            return;
        }
        if (avatarUrl.trim().isEmpty()) {
            profile.unsetAvatarUrl(now);  // 빈 문자열 → null 정규화 (아바타 제거)
        } else {
            profile.changeAvatarUrl(new AvatarUrl(avatarUrl), now);
        }
    }

    private void applyLocale(Profile profile, String locale, Instant now) {
        if (locale == null) {
            return;
        }
        if (locale.trim().isEmpty()) {
            // 명시적 빈 문자열 거부 — VO 화이트리스트 메시지 의존 회피 (code review M2)
            throw new IllegalArgumentException("locale must not be blank");
        }
        profile.changeLocale(new Locale(locale), now);
    }

    private MyProfileResult composeResult(User user, Profile profile) {
        List<OAuthProvider> providers = user.oauthIdentities().stream()
                .map(OAuthIdentity::provider)
                .toList();

        return new MyProfileResult(
                user.id(),
                user.email().orElse(null),
                user.displayName(),
                providers,
                user.createdAt(),
                profile.bio().orElse(null),
                profile.avatarUrl().orElse(null),
                profile.locale());
    }
}
