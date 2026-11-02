package com.opencrm.crm.recommendation;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.opencrm.crm.recommendation", importOptions = ImportOption.DoNotIncludeTests.class)
class RecommendationBoundaryTest {

    @ArchTest
    static final ArchRule local_advisory_only =
            noClasses()
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.opencrm.jev..",
                            "com.opencrm.ai..",
                            "com.opencrm.quote..",
                            "com.opencrm.product..",
                            "com.opencrm.configuration..",
                            "com.opencrm.crm.outbox..",
                            "com.opencrm.common.event..",
                            "org.springframework.web.client..",
                            "org.springframework.web.reactive.function.client..",
                            "org.springframework.kafka..",
                            "org.flywaydb..",
                            "java.net.http..")
                    .because("DRG-488 suggestions are local, advisory, and must not call a model, the catalog, or Kafka");
}
