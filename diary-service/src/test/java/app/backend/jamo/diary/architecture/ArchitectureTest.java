package app.backend.jamo.diary.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * diary-service 의 모듈 경계 / 계층 자동 검증.
 *
 * 자세한 규칙은 .claude/skills/module-boundary/references/archunit-rules.md 참조.
 * 본 골격은 R1 (다른 서비스 import 차단), R3 (domain Spring/JPA 의존 금지),
 * R8 (ai proto 직접 import 차단 — chat-service 만 허용, ADR-0003) 만 활성화.
 */
@AnalyzeClasses(
    packages = "app.backend.jamo.diary",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTest {

    /** R1 — 다른 Java 서비스 패키지 import 금지. */
    @ArchTest
    static final ArchRule no_import_from_other_services =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "app.backend.jamo.identity..",
                "app.backend.jamo.chat..",
                "app.backend.jamo.learning..",
                "app.backend.jamo.platform.."
            )
            .as("diary-service 는 다른 Java 서비스 모듈을 import 하지 않는다");

    /** R3 — domain 계층은 Spring/JPA 등 프레임워크에 의존하지 않는다. */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("domain 계층은 프레임워크에 의존하지 않는다");

    /** R6 — application 계층은 infrastructure 구현 클래스에 직접 의존하지 않는다 (port 인터페이스만). */
    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.diary.infrastructure..")
            .as("application 계층은 infrastructure 구현에 직접 의존하지 않는다 (의존 방향 안쪽)");

    /** R8 — ai-service 의 AiService gRPC 는 chat-service 만 호출한다 (ADR-0003). */
    @ArchTest
    static final ArchRule no_direct_ai_service_import =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.contracts.proto.ai..")
            .as("ai-service 의 AiService gRPC 는 chat-service 만 호출한다 (ADR-0003)");

    /** R12 — domain 계층은 Lombok annotation 을 사용하지 않는다 (ADR-0008 §C — 순수 Java / Invariant 명시 / 캡슐화). */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_lombok =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary.domain..")
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
