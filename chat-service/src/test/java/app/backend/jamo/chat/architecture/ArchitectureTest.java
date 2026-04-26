package app.backend.jamo.chat.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * chat-service 의 모듈 경계 / 계층 자동 검증.
 *
 * 자세한 규칙은 .claude/skills/module-boundary/references/archunit-rules.md 참조.
 * chat-service 는 ai-service 를 호출하는 유일한 서비스이므로 R8 대신 R8' 적용 —
 * ai proto 사용은 허용하되 infrastructure/grpc/client/ 에서만 (ADR-0003).
 */
@AnalyzeClasses(
    packages = "app.backend.jamo.chat",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTest {

    /** R1 — 다른 Java 서비스 패키지 import 금지. */
    @ArchTest
    static final ArchRule no_import_from_other_services =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.chat..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "app.backend.jamo.identity..",
                "app.backend.jamo.diary..",
                "app.backend.jamo.learning..",
                "app.backend.jamo.platform.."
            )
            .as("chat-service 는 다른 Java 서비스 모듈을 import 하지 않는다");

    /** R3 — domain 계층은 Spring/JPA 등 프레임워크에 의존하지 않는다. */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.chat.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("domain 계층은 프레임워크에 의존하지 않는다");

    /** R8' — ai proto 사용은 chat-service 의 infrastructure/grpc/client/ 에서만 (ADR-0003). */
    @ArchTest
    static final ArchRule ai_proto_only_in_grpc_client =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.chat..")
            .and().resideOutsideOfPackage("..infrastructure.grpc.client..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.contracts.proto.ai..")
            .as("chat-service 의 ai proto 사용은 infrastructure/grpc/client/ 에만 허용 (ADR-0003)");
}
