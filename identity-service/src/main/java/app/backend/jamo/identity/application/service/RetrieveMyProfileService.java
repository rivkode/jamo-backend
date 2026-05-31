package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveMyProfileQuery;
import app.backend.jamo.identity.application.port.DiaryCountPort;
import app.backend.jamo.identity.domain.exception.AuthenticatedUserNotFoundException;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 본인 프로필 조회 use case (PRD profile/getMyProfile.md §3, decisions/identity/profile-app-infra-decisions.md §결정 #2).
 *
 * <p><b>Lazy + Read 기본값</b> 패턴 — Profile aggregate 가 부재 (lazy 미생성 상태) 시 *기본값 합성*
 * 으로 응답하고 DB write 는 일으키지 않는다 (GET 의 idempotency 보존).
 *
 * <p>응답 합성: User aggregate 의 identity 5종 + Profile 의 외형 3종 (또는 기본값) + diary-service 의
 * diaryCount (Slice 3-b).
 *
 * <p><b>diaryCount gRPC 호출</b>: 본인 조회라 전체 일기 수 (includePrivate=true). diary-service
 * {@code GetDiaryService} 정합 — {@code @Transactional(readOnly)} 안에서 gRPC 호출 (DiaryCountGrpcClient 의
 * Circuit Breaker + Retry + 5s deadline 이 지연/장애를 제한, fallback=null). 조회 실패 시 diaryCount=null
 * (응답에 그대로 노출 — 프론트 "집계 불가"). DB read 와 단일 트랜잭션이라 self-invocation 우회 불필요.
 */
@Service
@RequiredArgsConstructor
public class RetrieveMyProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DiaryCountPort diaryCountPort;

    @Transactional(readOnly = true)
    public MyProfileResult retrieve(RetrieveMyProfileQuery query) {
        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                        "authenticated user not found: " + query.userId().value()));

        Optional<Profile> profileOpt = profileRepository.findById(query.userId());

        List<OAuthProvider> providers = user.oauthIdentities().stream()
                .map(OAuthIdentity::provider)
                .toList();

        // 본인 조회 — 전체 일기 수 (PUBLIC + PRIVATE). 실패 시 null.
        Long diaryCount = diaryCountPort.getCount(query.userId().value(), true);

        return new MyProfileResult(
                user.id(),
                user.email().orElse(null),
                user.displayName(),
                providers,
                user.createdAt(),
                profileOpt.flatMap(Profile::bio).orElse(null),
                profileOpt.flatMap(Profile::avatarUrl).orElse(null),
                profileOpt.map(Profile::locale).orElse(Locale.DEFAULT),
                diaryCount);
    }
}
