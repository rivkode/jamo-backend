package app.backend.jamo.identity.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    /** R3-b — domain 계층은 application/infrastructure/presentation 어떤 계층도 import 하지 않는다. */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.identity.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "app.backend.jamo.identity.application..",
                "app.backend.jamo.identity.infrastructure..",
                "app.backend.jamo.identity.presentation.."
            )
            .as("domain 은 외곽 계층(application/infrastructure/presentation) 에 의존하지 않는다 (DDD 의존성 역전)");

    /** R4 — Presentation 은 Repository 인터페이스를 직접 의존하지 않는다 (Application Service 경유). */
    @ArchTest
    static final ArchRule presentation_should_not_depend_on_repository =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.identity.presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.identity.domain.repository..")
            .as("Controller 는 Application Service 만 사용한다 (Repository 직접 호출 금지)");

    /** R6 — @RestController 는 ..presentation.controller.. 에만 위치한다. */
    @ArchTest
    static final ArchRule rest_controllers_must_reside_in_presentation_controller =
        classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..presentation.controller..")
            .as("@RestController 는 ..presentation.controller.. 에만 위치한다");

    /** R12 — domain 계층은 Lombok annotation 을 사용하지 않는다 (ADR-0008 §C — 순수 Java / Invariant 명시 / 캡슐화). */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_lombok =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.identity.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("lombok..")
            .as("domain 계층은 Lombok annotation 을 사용하지 않는다 (ADR-0008 §C)");

    /** R13 — @Data / @Setter / @AllArgsConstructor 전 layer 금지 (ADR-0008 §B 블랙리스트). */
    @ArchTest
    static final ArchRule no_lombok_data_or_setter_or_all_args =
        noClasses()
            .should().beAnnotatedWith("lombok.Data")
            .orShould().beAnnotatedWith("lombok.Setter")
            .orShould().beAnnotatedWith("lombok.AllArgsConstructor")
            .as("@Data/@Setter/@AllArgsConstructor 는 캡슐화/invariant 우회 위험으로 전 layer 금지 (ADR-0008 §B)");
}
