package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 타 사용자 프로필 조회 use case — public-safe 4 필드 (PRD profile/getProfile.md §3,
 * decisions/identity/profile-prd-evaluation.md §결정 #2).
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 노출 X (UserSummaryService
 * 정합). viewer-context (follow 여부) 미적용 — follow 도메인 부재로 Non-Goal.
 *
 * <p>인증 검증 ({@code @LoginUser}) 은 Presentation 책임. follow 도메인 도입 시 query 에 `viewerId`
 * 추가 + viewer-context 합성 (Phase 6-b-c, code-reviewer M1 후속 박제).
 */
@Service
@RequiredArgsConstructor
public class RetrieveProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public PublicProfileResult retrieve(RetrieveProfileQuery query) {
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
