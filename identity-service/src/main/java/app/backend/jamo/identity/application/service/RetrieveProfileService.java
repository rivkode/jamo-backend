package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.application.port.DiaryCountPort;
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
 * 타 사용자 프로필 조회 use case — public-safe 4 필드 + diaryCount (PRD profile/getProfile.md §3,
 * decisions/identity/profile-prd-evaluation.md §결정 #2).
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 노출 X (UserSummaryService
 * 정합). viewer-context (follow 여부) 미적용 — follow 도메인 부재로 Non-Goal.
 *
 * <p><b>diaryCount (Slice 3-b)</b>: 타인 조회라 <b>공개(PUBLIC) 일기 수만</b> (includePrivate=false) —
 * 비공개 수 누설 차단 (IDOR, PRD §1.6). {@code RetrieveMyProfileService} 의 gRPC 호출 정책 정합 (트랜잭션
 * 안, fallback=null).
 *
 * <p>인증 검증 ({@code @LoginUser}) 은 Presentation 책임. follow 도메인 도입 시 query 에 `viewerId`
 * 추가 + viewer-context 합성 (Phase 6-b-c, code-reviewer M1 후속 박제).
 */
@Service
@RequiredArgsConstructor
public class RetrieveProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DiaryCountPort diaryCountPort;

    @Transactional(readOnly = true)
    public PublicProfileResult retrieve(RetrieveProfileQuery query) {
        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new UserNotFoundException("user not found: " + query.userId().value()));

        Optional<Profile> profileOpt = profileRepository.findById(query.userId());

        // 타인 조회 — 공개 일기 수만 (includePrivate=false, IDOR 차단). 실패 시 null.
        Long diaryCount = diaryCountPort.getCount(query.userId().value(), false);

        return new PublicProfileResult(
                user.id(),
                user.displayName(),
                profileOpt.flatMap(Profile::bio).orElse(null),
                profileOpt.flatMap(Profile::avatarUrl).orElse(null),
                diaryCount);
    }
}
