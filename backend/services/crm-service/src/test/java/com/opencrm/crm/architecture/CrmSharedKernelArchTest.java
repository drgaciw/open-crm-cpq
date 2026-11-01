package com.opencrm.crm.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AC-1: crm-service may depend on {@code AccountId} and {@code Money} from
 * {@code com.opencrm.common.valueobject}, and on nothing else in that package.
 * {@code Percentage} is forbidden. Organization and contact services and mappers
 * must use {@code AccountId} as the account identifier.
 */
@AnalyzeClasses(packages = "com.opencrm.crm", importOptions = ImportOption.DoNotIncludeTests.class)
class CrmSharedKernelArchTest {

    private static final String ACCOUNT_ID = "com.opencrm.common.valueobject.AccountId";
    private static final String MONEY = "com.opencrm.common.valueobject.Money";
    private static final String PERCENTAGE = "com.opencrm.common.valueobject.Percentage";

    private static final DescribedPredicate<JavaClass> VALUE_OBJECT_OTHER_THAN_ACCOUNT_ID_AND_MONEY =
            new DescribedPredicate<>("types in com.opencrm.common.valueobject other than AccountId and Money") {
                @Override
                public boolean test(JavaClass input) {
                    String name = input.getName();
                    if (!name.startsWith("com.opencrm.common.valueobject.")) {
                        return false;
                    }
                    return !ACCOUNT_ID.equals(name) && !MONEY.equals(name);
                }
            };

    @ArchTest
    static final ArchRule only_account_id_and_money_from_the_shared_kernel =
            noClasses()
                    .that().resideInAPackage("com.opencrm.crm..")
                    .should().dependOnClassesThat(VALUE_OBJECT_OTHER_THAN_ACCOUNT_ID_AND_MONEY)
                    .because("AccountId and Money are the only shared-kernel value types crm-service may use");

    @ArchTest
    static final ArchRule percentage_is_forbidden =
            noClasses()
                    .that().resideInAPackage("com.opencrm.crm..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(PERCENTAGE)
                    .because("Percentage is not a shared-kernel type for crm-service");

    @ArchTest
    static final ArchRule organization_and_contact_use_account_id =
            classes()
                    .that().haveFullyQualifiedName("com.opencrm.crm.organization.OrganizationService")
                    .or().haveFullyQualifiedName("com.opencrm.crm.organization.OrganizationMapper")
                    .or().haveFullyQualifiedName("com.opencrm.crm.contact.ContactService")
                    .or().haveFullyQualifiedName("com.opencrm.crm.contact.ContactMapper")
                    .should().dependOnClassesThat().haveFullyQualifiedName(ACCOUNT_ID)
                    .because("AccountId is the shared-kernel identifier on organization and contact service and mapper signatures");
}
