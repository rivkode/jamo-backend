package app.backend.jamo.diary.infrastructure.config;

import app.backend.jamo.common.auth.BlacklistChecker;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.common.auth.KeyProvider;
import app.backend.jamo.common.auth.RsaJwtVerifier;
import app.backend.jamo.common.auth.RsaKeyPairKeyProvider;
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
@EnableConfigurationProperties({SentenceFeedbackRateLimitProperties.class, JwtVerifierProperties.class})
public class DiaryServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // ============================================================
    // JWT verify Bean 그래프 (security-reviewer C1) — diary-service 가 access token 검증만.
    // identity-service IdentityServiceConfig 의 verify-only 슬림 버전.
    // BlacklistChecker 는 SessionBlacklistRedisChecker @Component 자동 주입.
    // ============================================================

    @Bean
    public KeyProvider jwtVerifierKeyProvider(JwtVerifierProperties props) {
        return new RsaKeyPairKeyProvider(
            JwkPemReader.readVerificationKey(props.publicKeyPem(), props.keyId())
        );
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
