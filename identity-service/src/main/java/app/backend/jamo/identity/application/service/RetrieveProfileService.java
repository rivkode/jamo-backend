package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * 타 사용자 프로필 조회 use case — public-safe 4 필드 (PRD profile/getProfile.md §3,
 * decisions/identity/profile-prd-evaluation.md §결정 #2).
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 노출 X (UserSummaryService
 * 정합). viewer-context (follow 여부) 미적용 — follow 도메인 부재로 Non-Goal.
 *
 * <p>{@code loginUserId} 는 인증 검증 외 미사용 — 향후 follow 도메인 도입 시 viewer-context 합성에
 * 활용 예정.
 */
@Service
public class RetrieveProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public RetrieveProfileService(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
    }

    @Transactional(readOnly = true)
    public PublicProfileResult retrieve(RetrieveProfileQuery query) {
        Objects.requireNonNull(query, "query");

        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new UserNotFoundException("user not found: " + query.userId().value()));

        Optional<Profile> profileOpt = profileRepository.findById(query.userId());

        return new PublicProfileResult(
                user.id(),
                user.displayName(),
                profileOpt.flatMap(Profile::bio).orElse(null),
                profileOpt.flatMap(Profile::avatarUrl).orElse(null));
    }
}
