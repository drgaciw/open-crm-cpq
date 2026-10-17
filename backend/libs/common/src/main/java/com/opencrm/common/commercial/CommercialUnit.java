package com.opencrm.common.commercial;

import java.util.List;

/** One application unit in the commercial projection, sorted by unit number. */
public record CommercialUnit(
        int unitNo,
        Integer dependencyOrder,
        String targetType,
        String targetRef,
        Long observedCommercialRevision) {

    List<JcsCanonicalizer.Value.Field> fields() {
        return List.of(
                new JcsCanonicalizer.Value.Field("dependencyOrder", number(dependencyOrder)),
                new JcsCanonicalizer.Value.Field("observedCommercialRevision", number(observedCommercialRevision)),
                new JcsCanonicalizer.Value.Field("targetRef", text(targetRef)),
                new JcsCanonicalizer.Value.Field("targetType", text(targetType)),
                new JcsCanonicalizer.Value.Field("unitNo", new JcsCanonicalizer.Value.IntNum(unitNo)));
    }

    private static JcsCanonicalizer.Value number(Number value) {
        return value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.IntNum(value.longValue());
    }

    private static JcsCanonicalizer.Value text(String value) {
        return value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.Str(value);
    }
}
