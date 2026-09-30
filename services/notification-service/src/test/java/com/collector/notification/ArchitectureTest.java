package com.collector.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** L'architecture hexagonale est vérifiée : une violation fait échouer le build. */
@AnalyzeClasses(packages = "com.collector.notification", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonal = onionArchitecture()
            .domainModels("..domain..")
            .applicationServices("..application..")
            .adapter("web", "..adapter.in.web..")
            .adapter("messaging-in", "..adapter.in.messaging..")
            .adapter("persistence", "..adapter.out.persistence..")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "tools.jackson..", "com.fasterxml.jackson.databind..",
                    "com.collector.messaging..");

    @ArchTest
    static final ArchRule applicationUsesNoTechnicalFramework = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.amqp..", "org.springframework.jdbc..",
                    "org.springframework.security..", "jakarta..", "com.collector.messaging..");

    @ArchTest
    static final ArchRule noCycles = slices().matching("com.collector.notification.(*)..").should().beFreeOfCycles();
}
