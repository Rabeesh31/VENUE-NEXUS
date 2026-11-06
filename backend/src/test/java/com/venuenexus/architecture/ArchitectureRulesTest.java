package com.venuenexus.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.venuenexus", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureRulesTest {

    /**
     * Rule 1: Controllers must never access repositories directly.
     * Required application flow: Controller -> Service -> Repository
     */
    @ArchTest
    static final ArchRule controllers_must_not_access_repositories = noClasses()
            .that().resideInAPackage("..controller..")
            .or().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .orShould().dependOnClassesThat().areAnnotatedWith(Repository.class)
            .because("Controllers must not access repositories directly; all interactions must flow through services.");

    /**
     * Rule 2: Services must not depend on controllers.
     */
    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers = noClasses()
            .that().resideInAPackage("..service..")
            .or().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .orShould().dependOnClassesThat().areAnnotatedWith(RestController.class)
            .orShould().dependOnClassesThat().areAnnotatedWith(Controller.class)
            .because("Services must be decoupled from the transport/controller layer.");

    /**
     * Rule 3: Common package must not depend on feature modules.
     */
    @ArchTest
    static final ArchRule common_must_not_depend_on_feature_modules = noClasses()
            .that().resideInAPackage("com.venuenexus.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.venuenexus.identity..",
                    "com.venuenexus.venue..",
                    "com.venuenexus.resource..",
                    "com.venuenexus.outbox.."
            )
            .because("Common package must remain independent of specific business feature modules.");

    /**
     * Rule 4: Slices representing top-level modules should be free of cyclic dependencies.
     */
    @ArchTest
    static final ArchRule no_cycles_between_top_level_modules = slices()
            .matching("com.venuenexus.(*)..")
            .should().beFreeOfCycles()
            .because("Modular monolith components must maintain acyclic dependencies.");

    /**
     * Rule 5: Production code must never use System.out or System.err.
     */
    @ArchTest
    static final ArchRule no_system_out_or_err_in_production_code = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("All logging must use structured SLF4J / Logback logging with MDC traceId.");
}
