package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveMyProfileQuery;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 본인 프로필 조회 use case (PRD profile/getMyProfile.md §3, decisions/identity/profile-app-infra-decisions.md §결정 #2).
 *
 * <p><b>Lazy + Read 기본값</b> 패턴 — Profile aggregate 가 부재 (lazy 미생성 상태) 시 *기본값 합성*
 * 으로 응답하고 DB write 는 일으키지 않는다 (GET 의 idempotency 보존).
 *
 * <p>응답 합성: User aggregate 의 identity 5종 + Profile 의 외형 3종 (또는 기본값).
 */
@Service
public class RetrieveMyProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public RetrieveMyProfileService(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
    }

    @Transactional(readOnly = true)
    public MyProfileResult retrieve(RetrieveMyProfileQuery query) {
        Objects.requireNonNull(query, "query");

        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new UserNotFoundException("user not found: " + query.userId().value()));

        Optional<Profile> profileOpt = profileRepository.findById(query.userId());

        List<OAuthProvider> providers = user.oauthIdentities().stream()
                .map(OAuthIdentity::provider)
                .toList();

        return new MyProfileResult(
                user.id(),
                user.email().orElse(null),
                user.displayName(),
                providers,
                user.createdAt(),
                profileOpt.flatMap(Profile::bio).orElse(null),
                profileOpt.flatMap(Profile::avatarUrl).orElse(null),
                profileOpt.map(Profile::locale).orElse(Locale.DEFAULT));
    }
}
