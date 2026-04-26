package app.backend.jamo.identity.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * identity-service 의 모듈 경계 / 계층 자동 검증.
 *
 * 자세한 규칙은 .claude/skills/module-boundary/references/archunit-rules.md 참조.
 * 본 골격은 R1 (다른 서비스 import 차단), R3 (domain Spring/JPA 의존 금지) 만 활성화.
 * 나머지 R4~R9 는 해당 코드(Controller / @GrpcService / @KafkaListener 등) 등장 시점에 추가.
 */
@AnalyzeClasses(
    packages = "app.backend.jamo.identity",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTest {

    /** R1 — 다른 Java 서비스 패키지 import 금지 (서비스 경계 침범 차단). */
    @ArchTest
    static final ArchRule no_import_from_other_services =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.identity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "app.backend.jamo.diary..",
                "app.backend.jamo.chat..",
                "app.backend.jamo.learning..",
                "app.backend.jamo.platform.."
            )
            .as("identity-service 는 다른 Java 서비스 모듈을 import 하지 않는다 (contracts / common-* 만 허용)");

    /** R3 — domain 계층은 Spring/JPA 등 프레임워크에 의존하지 않는다 (DDD). */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.identity.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("domain 계층은 프레임워크에 의존하지 않는다");

    /** R10 — JPA 연관관계 어노테이션 금지 (ADR-0005). 외래 키는 ID 컬럼만 보유. */
    @ArchTest
    static final ArchRule no_jpa_relationship_annotations =
        noFields()
            .should().beAnnotatedWith(jakarta.persistence.ManyToOne.class)
            .orShould().beAnnotatedWith(jakarta.persistence.OneToMany.class)
            .orShould().beAnnotatedWith(jakarta.persistence.OneToOne.class)
            .orShould().beAnnotatedWith(jakarta.persistence.ManyToMany.class)
            .as("JPA 연관관계 어노테이션 금지 — ID 컬럼만 보유 (ADR-0005)");
}
