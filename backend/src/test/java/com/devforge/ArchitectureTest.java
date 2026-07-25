package com.devforge;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Executable architecture rules.
 *
 * <p>The loose coupling this codebase is built around is a property that erodes
 * silently: one convenient import of another module's service and the boundary is
 * gone, with nothing failing. These tests make that a build error instead of a
 * code-review question.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.devforge");

    /**
     * The central rule: a feature module may only see another module's
     * {@code contract} package, never its entities, repositories, or services.
     */
    @Test
    void modulesTalkOnlyThroughPublishedContracts() {
        for (String module : new String[]{"identity", "workspace", "document", "task"}) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.devforge.%s..".formatted(module))
                    .and().resideInAPackage("com.devforge.(identity|workspace|document|task)..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.devforge.%s.domain..".formatted(module),
                            "com.devforge.%s.infrastructure..".formatted(module),
                            "com.devforge.%s.api..".formatted(module))
                    .because("""
                            %s internals must stay private to it; other modules use \
                            com.devforge.%s.contract"""
                            .formatted(module, module));

            rule.check(PRODUCTION_CLASSES);
        }
    }

    /**
     * A module's {@code application} layer is visible to its own module only. This
     * is what forces cross-module calls onto interfaces such as
     * {@code WorkspaceAccess} rather than concrete services.
     */
    @Test
    void applicationServicesAreNotSharedAcrossModules() {
        for (String module : new String[]{"identity", "workspace", "document", "task"}) {
            noClasses()
                    .that().resideOutsideOfPackage("com.devforge.%s..".formatted(module))
                    .and().resideInAPackage("com.devforge.(identity|workspace|document|task)..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.devforge.%s.application..".formatted(module))
                    .because("cross-module calls must go through a contract interface")
                    .check(PRODUCTION_CLASSES);
        }
    }

    /** Contracts are the published surface, so they must not leak internals. */
    @Test
    void contractPackagesDependOnNothingInternal() {
        noClasses()
                .that().resideInAPackage("com.devforge..contract..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.devforge..domain..",
                        "com.devforge..application..",
                        "com.devforge..infrastructure..",
                        "com.devforge..api..")
                .because("a contract that references internals is not a boundary")
                .check(PRODUCTION_CLASSES);
    }

    /** Within a module, dependencies point inward: api -> application -> domain. */
    @Test
    void respectsLayeringWithinModules() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Api").definedBy("com.devforge..api..")
                .layer("Application").definedBy("com.devforge..application..")
                .layer("Domain").definedBy("com.devforge..domain..")
                .layer("Infrastructure").definedBy("com.devforge..infrastructure..")
                .whereLayer("Api").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
                .check(PRODUCTION_CLASSES);
    }

    /** Domain types must not know about the web or persistence frameworks. */
    @Test
    void domainDoesNotDependOnSpringWebOrData() {
        noClasses()
                .that().resideInAPackage("com.devforge..domain..")
                .and().areNotInterfaces()
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "org.springframework.stereotype..")
                .because("entities and domain services must be framework-free")
                .check(PRODUCTION_CLASSES);
    }

    /** Controllers must not reach past the application layer into persistence. */
    @Test
    void controllersDoNotUseRepositories() {
        noClasses()
                .that().resideInAPackage("com.devforge..api..")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("controllers coordinate services, not storage")
                .check(PRODUCTION_CLASSES);
    }

    /** Persistence must stay behind Spring Data interfaces. */
    @Test
    void repositoriesAreInterfacesInDomainPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces()
                .andShould().resideInAPackage("com.devforge..domain..")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * Guards the specific bug this design removed: entities were previously
     * reachable across modules, so a document held a live {@code Workspace}.
     */
    @Test
    void entitiesAreNotExposedOutsideTheirModule() {
        noClasses()
                .that().resideInAPackage("com.devforge.document..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.devforge.workspace.domain..", "com.devforge.task.domain..")
                .because("a document must reference a workspace by id, not by entity")
                .check(PRODUCTION_CLASSES);
    }
}
