package com.collector.catalogue;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

/** L'architecture hexagonale est vérifiée : une violation fait échouer le build. */
@AnalyzeClasses(packages = "com.collector.catalogue", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonal = onionArchitecture()
            .domainModels("..domain..")
            .applicationServices("..application..")
            .adapter("web", "..adapter.in.web..")
            .adapter("messaging-in", "..adapter.in.messaging..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("outbox", "..adapter.out.outbox..")
            .adapter("storage", "..adapter.out.storage..")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "tools.jackson..",
                    "com.fasterxml.jackson.databind..", "software.amazon..");

    @ArchTest
    static final ArchRule applicationUsesNoTechnicalFramework = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.amqp..",
                    "jakarta.persistence..", "software.amazon..", "org.springframework.jdbc..",
                    "org.springframework.security..");

    @ArchTest
    static final ArchRule featuresAreFreeOfCycles = slices()
            .matching("com.collector.catalogue.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule entitiesStayInPersistence = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("..adapter.out.persistence..", "..adapter.out.outbox..");

    // Une fonctionnalité ne dépend que du noyau partagé, jamais du code d'une autre.
    @ArchTest
    static final ArchRule featuresAreIndependent = noClasses().that().resideInAPackage("..article..")
            .should().dependOnClassesThat().resideInAnyPackage("..interest..", "..category..", "..ping..");
}
