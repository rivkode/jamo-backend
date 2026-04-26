package app.backend.jamo.contracts.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * contracts 모듈 자체의 검증 규칙.
 *
 * 자세한 규칙은 .claude/skills/module-boundary/references/archunit-rules.md 참조.
 * 본 골격은 R2 (contracts 에 Spring/JPA/Jackson 어노테이션 금지) 만 활성화.
 *
 * 이벤트 record 의 eventId / occurredAt 필드 보유는 ArchUnit 으로 강제 어렵 — PR 리뷰 + 템플릿으로 보강.
 */
@AnalyzeClasses(
    packages = "app.backend.jamo.contracts",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ContractsArchitectureTest {

    /** R2 — contracts 모듈은 프레임워크 의존성 없는 순수 record + proto 만 보유. */
    @ArchTest
    static final ArchRule no_spring_or_jpa_in_contracts =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.contracts..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("contracts 모듈은 프레임워크 의존성 없는 순수 record + proto 만 보유 (ADR-0002 §3)");
}
