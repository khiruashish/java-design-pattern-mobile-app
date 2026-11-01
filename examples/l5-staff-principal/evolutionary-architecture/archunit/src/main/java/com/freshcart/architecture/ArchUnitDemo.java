package com.freshcart.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real ArchUnit: architecture rules as code, evaluated against FreshCart's compiled classes. */
public class ArchUnitDemo {

    static final Map<String, ArchRule> RULES = new LinkedHashMap<>();
    static {
        RULES.put("internal packages are private to their module", noClasses()                          // (1)!
                .that().resideOutsideOfPackage("com.freshcart.pricing..")
                .should().dependOnClassesThat().resideInAPackage("com.freshcart.pricing.internal.."));
        RULES.put("layers: web -> ordering -> pricing", layeredArchitecture().consideringOnlyDependenciesInLayers()   // (2)!
                .layer("Web").definedBy("com.freshcart.web..")
                .layer("Ordering").definedBy("com.freshcart.ordering..")
                .layer("Pricing").definedBy("com.freshcart.pricing..")
                .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                .whereLayer("Ordering").mayOnlyBeAccessedByLayers("Web")
                .whereLayer("Pricing").mayOnlyBeAccessedByLayers("Ordering"));
        RULES.put("no cycles between modules", slices().matching("com.freshcart.(*)..").should().beFreeOfCycles());   // (3)!
    }

    public static JavaClasses freshcartClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.freshcart.web", "com.freshcart.ordering", "com.freshcart.pricing");
    }

    public static void main(String[] args) {
        JavaClasses classes = freshcartClasses();
        RULES.forEach((name, rule) -> {
            EvaluationResult result = rule.evaluate(classes);                                             // (4)!
            System.out.println((result.hasViolation() ? "FAIL " : "ok   ") + name);
            result.getFailureReport().getDetails().stream().sorted()
                    .forEach(d -> System.out.println("     " + d.replaceAll(" in \\(.*\\)$", "")));
        });
    }
}
