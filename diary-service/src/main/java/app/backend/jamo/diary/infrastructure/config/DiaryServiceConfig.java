package app.backend.jamo.diary.infrastructure.config;

import app.backend.jamo.common.auth.BlacklistChecker;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.common.auth.KeyProvider;
import app.backend.jamo.common.auth.RsaJwtVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * diary-service infrastructure-level bean 정의.
 *
 * <ul>
 *   <li>{@link Clock} — Application Service / Aggregate 가 시간 의존을 명시적으로 받도록 UTC bean 등록
 *       (테스트 시 fixed Clock 으로 대체 가능)</li>
 *   <li>{@link TransactionTemplate} — RequestSentenceFeedbackService 의 2-트랜잭션 분리 패턴</li>
 *   <li>{@code @EnableScheduling} — {@code OutboxPoller} 의 {@code @Scheduled} 활성화</li>
 *   <li>{@code @EnableKafka} — {@code @KafkaListener} 명시 등록 (code-reviewer C2 — 자동 구성 의존 회피)</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties({
    SentenceFeedbackRateLimitProperties.class,
    SentenceFeedbackBatchProperties.class,
    JwtVerifierProperties.class,
    CorsProperties.class,
    AudioStorageProperties.class
})
public class DiaryServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * diarychat 롱폴(poll)의 DeferredResult 주기 체크용 스케줄러 — servlet thread 비점유 (daemon).
     *
     * <p>단일 인스턴스 in-memory — 다중 인스턴스 확장은 Redis pub/sub 또는 DB notify (후속, v2 §8-b).
     * pool 4: idle 주기 체크는 가벼운 row 존재 조회뿐(새 메시지 없으면 UserSummary gRPC 미호출 —
     * assembler 가 빈 목록 즉시 반환). gRPC(displayName 조립, deadline 5s)는 새 메시지가 도착한
     * <b>종료 직전 체크</b>에서만 1회. 그 순간 thread 점유는 외부 지연에 노출되므로(code-reviewer H1) 동시
     * 대량 롱폴 + identity 지연 시 pool 압박 가능 — 단일 인스턴스 수용, 확장 시 위 후속과 함께 해소.
     */
    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ScheduledExecutorService chatPollScheduler() {
        return java.util.concurrent.Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "chat-poll");
            t.setDaemon(true);
            return t;
        });
    }

    // ============================================================
    // JWT verify Bean 그래프 (security-reviewer C1) — diary-service 가 access token 검증만.
    // identity-service IdentityServiceConfig 의 verify-only 슬림 버전.
    // BlacklistChecker 는 SessionBlacklistRedisChecker @Component 자동 주입.
    // ============================================================

    @Bean
    public KeyProvider jwtVerifierKeyProvider(JwtVerifierProperties props) {
        // diary-service 는 verify-only — common-auth-jwt 의 RsaKeyPairKeyProvider 는 signing key (private 포함)
        // 를 강제하므로 verify 전용 어댑터로 우회한다. common 모듈에 VerifyOnlyKeyProvider 추출은 후속 PR.
        RSAKey verificationKey = JwkPemReader.readVerificationKey(props.publicKeyPem(), props.keyId());
        return new KeyProvider() {
            @Override
            public RSAKey signingKey() {
                throw new UnsupportedOperationException("diary-service is verify-only");
            }

            @Override
            public RSAKey verificationKey() {
                return verificationKey;
            }
        };
    }

    @Bean
    public JwtVerifier jwtVerifier(KeyProvider keyProvider, JwtVerifierProperties props,
                                   BlacklistChecker blacklistChecker, Clock clock) {
        return new RsaJwtVerifier(
            keyProvider,
            props.issuer(),
            props.audience(),
            blacklistChecker,
            clock,
            props.clockSkew()
        );
    }
}
