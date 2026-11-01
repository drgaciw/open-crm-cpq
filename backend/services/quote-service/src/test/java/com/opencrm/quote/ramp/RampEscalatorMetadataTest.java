package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.LineItemCreateRequest;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Authoring choices must survive the same attach/read path used by line writes. */
class RampEscalatorMetadataTest {

    private final RampEscalatorExpander expander = new RampEscalatorExpander();
    private RampScheduleAttacher attacher;

    @BeforeEach
    void setUp() {
        RampPricingProperties properties = new RampPricingProperties();
        properties.setEnabled(true);
        attacher = new RampScheduleAttacher(properties, new RampScheduleValidator(), expander);
    }

    @Test
    void nonCompoundingChoiceSurvivesLineAttachAndDtoRead() {
        RampEscalatorDto escalator = escalator(false);
        QuoteLineItem line = attach(escalator);

        assertThat(line.getRampEscalatorCompounding()).isFalse();
        RampScheduleDto read = RampScheduleViews.toDto(line);
        assertThat(read.getEscalator().getCompounding()).isFalse();
        assertThat(read.getEscalator().getStartPrice()).isEqualByComparingTo("100.0000");
        assertThat(read.getSteps()).hasSize(1);
        // Explicit authored prices remain authoritative over generator metadata.
        assertThat(read.getSteps().getFirst().getUnitPrice()).isEqualByComparingTo("125.0000");
    }

    @Test
    void legacyNullChoiceRemainsNullAndStillCompoundsAfterRead() {
        QuoteLineItem line = attach(escalator(null));
        RampEscalatorDto read = RampScheduleViews.toDto(line).getEscalator();

        assertThat(line.getRampEscalatorCompounding()).isNull();
        assertThat(read.getCompounding()).isNull();
        assertThat(expander.expand(36, read, 1)).extracting(RampStepDto::getUnitPrice)
                .containsExactly(new BigDecimal("100.0000"), new BigDecimal("110.0000"),
                        new BigDecimal("121.0000"));
    }

    @Test
    void clearingScheduleAlsoClearsTheCompoundingChoice() {
        QuoteLineItem line = attach(escalator(false));

        attacher.attach(line, null);

        assertThat(line.getRampEscalatorCompounding()).isNull();
        assertThat(line.getRampEscalatorMode()).isNull();
        assertThat(line.getRampEscalatorStartPrice()).isNull();
        assertThat(line.getRampEscalatorAmount()).isNull();
        assertThat(line.getRampEscalatorEveryMonths()).isNull();
        assertThat(RampScheduleViews.toDto(line)).isNull();
    }

    @Test
    void nonCompoundingExtensionUsesTheSameNormalizedBaseBeforeAndAfterRead() {
        RampEscalatorDto authored = escalator(false);
        authored.setStartPrice(new BigDecimal("100.12345"));
        authored.setAmount(new BigDecimal("10.12345"));
        QuoteLineItem line = attach(authored);
        RampScheduleDto read = RampScheduleViews.toDto(line);

        assertThat(read.getEscalator().getStartPrice()).isEqualByComparingTo("100.1235");
        assertThat(read.getEscalator().getAmount()).isEqualByComparingTo("10.1235");
        List<RampStepDto> extended = expander.extend(read.getSteps(), 60, read.getEscalator());
        assertThat(extended).extracting(RampStepDto::getUnitPrice)
                .containsExactly(new BigDecimal("125.0000"), new BigDecimal("135.1360"),
                        new BigDecimal("145.2720"));
        assertThat(expander.extend(read.getSteps(), 60, authored))
                .extracting(RampStepDto::getUnitPrice)
                .containsExactlyElementsOf(extended.stream().map(RampStepDto::getUnitPrice).toList());
    }

    private QuoteLineItem attach(RampEscalatorDto escalator) {
        Quote quote = Quote.builder().durationInMonths(36).build();
        RampStepDto step = RampStepDto.builder().sequence(1).startMonth(1).months(36)
                .quantity(1).priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal("125"))
                .build();
        LineItemCreateRequest request = LineItemCreateRequest.builder()
                .productExternalId("subscription-seat").quantity(1).listPrice(new BigDecimal("100"))
                .billingFrequency("MONTHLY").lineItemType("SUBSCRIPTION")
                .schedule(RampScheduleDto.builder().steps(List.of(step)).escalator(escalator).build())
                .build();
        QuoteLineItem line = QuoteLineItem.builder().quote(quote).build();
        attacher.attach(line, attacher.prepare(quote, request));
        return line;
    }

    private static RampEscalatorDto escalator(Boolean compounding) {
        return RampEscalatorDto.builder().startPrice(new BigDecimal("100"))
                .mode(RampEscalatorMode.PERCENT).amount(new BigDecimal("10")).everyMonths(12)
                .compounding(compounding).build();
    }
}
