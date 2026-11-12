package com.venuenexus.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.venuenexus", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories_directly =
            noClasses().that().areAnnotatedWith(RestController.class)
                    .or().areAnnotatedWith(Controller.class)
                    .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                    .allowEmptyShould(true)
                    .because("Controllers must delegate to services and never access repositories directly");

    @ArchTest
    static final ArchRule services_should_be_in_service_or_authz_or_jwt_or_qr_packages =
            classes().that().haveSimpleNameEndingWith("Service")
                    .should().resideInAnyPackage(
                            "..service..",
                            "..jwt..",
                            "..qr..",
                            "..metrics.."
                    );
}
