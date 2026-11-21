package com.opencrm.ai.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengerVariantGeneratorTest {

    private final ChallengerVariantGenerator generator = new ChallengerVariantGenerator();

    @Test
    void mutatesFirstDecimalLiteral() {
        String control = "rule \"X\" when then $q.setDiscount(0.10); end";
        List<ChallengerVariantGenerator.GeneratedVariant> variants = generator.generate(control, 2);

        assertEquals(2, variants.size());
        assertTrue(variants.get(0).ruleContent().contains("0.0850")
                || variants.get(0).ruleContent().contains("0.085"));
        assertFalse(variants.get(0).ruleContent().equals(control));
    }

    @Test
    void clonesWhenNoDecimalPresent() {
        String control = "rule \"X\" when then end";
        List<ChallengerVariantGenerator.GeneratedVariant> variants = generator.generate(control, 1);

        assertEquals(1, variants.size());
        assertEquals(control, variants.get(0).ruleContent());
        assertTrue(variants.get(0).description().contains("no numeric literal"));
    }
}
