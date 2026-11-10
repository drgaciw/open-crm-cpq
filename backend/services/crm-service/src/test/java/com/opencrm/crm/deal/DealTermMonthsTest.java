package com.opencrm.crm.deal;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-636: the deal term in months travels through the request, entity and DTO, and must be positive. */
@DisplayName("Deal term in months")
class DealTermMonthsTest {

    private static final DealMapper MAPPER = Mappers.getMapper(DealMapper.class);
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    @DisplayName("a term on create reaches the entity and comes back on the DTO")
    void createCarriesTheTerm() {
        Deal deal = MAPPER.toEntity(DealCreateRequest.builder().name("Three year licence").termMonths(36).build());

        assertThat(deal.getTermMonths()).isEqualTo(36);
        assertThat(MAPPER.toDto(deal).getTermMonths()).isEqualTo(36);
    }

    @Test
    @DisplayName("a deal created without a term has none")
    void termIsOptional() {
        DealCreateRequest request = DealCreateRequest.builder().name("No term yet").build();

        assertThat(validator.validate(request)).isEmpty();
        assertThat(MAPPER.toEntity(request).getTermMonths()).isNull();
    }

    @Test
    @DisplayName("an update sets the term; an update without one leaves it unchanged")
    void updateSetsOrKeepsTheTerm() {
        Deal deal = Deal.builder().name("Renewal").termMonths(12).build();

        MAPPER.updateEntity(DealUpdateRequest.builder().termMonths(24).build(), deal);
        assertThat(deal.getTermMonths()).isEqualTo(24);

        MAPPER.updateEntity(DealUpdateRequest.builder().name("Renewal FY27").build(), deal);
        assertThat(deal.getTermMonths()).isEqualTo(24);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("zero and negative terms are rejected on create and update")
    void termMustBePositive(int months) {
        assertThat(validator.validate(DealCreateRequest.builder().name("Bad term").termMonths(months).build()))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("termMonths");
        assertThat(validator.validate(DealUpdateRequest.builder().termMonths(months).build()))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("termMonths");
    }
}
