package com.opencrm.ai.profile;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record SellingProfile(Fields fields, String companyUrl, String fetchedSummary, Instant fetchedAt,
                             String summarySha256, String updatedBy, Instant updatedAt, long version) {
    public record Fields(@Size(max=4000) String companyOverview, @Size(max=4000) String productOverview,
                         @Size(max=4000) String valuePropositions, @Size(max=4000) String targetSegments,
                         @Size(max=4000) String differentiatorsCompetitors,
                         @Size(max=4000) String commercialGuardrails, @Size(max=4000) String toneGuidelines) {
        public Fields {
            companyOverview = bounded(companyOverview); productOverview = bounded(productOverview);
            valuePropositions = bounded(valuePropositions); targetSegments = bounded(targetSegments);
            differentiatorsCompetitors = bounded(differentiatorsCompetitors);
            commercialGuardrails = bounded(commercialGuardrails); toneGuidelines = bounded(toneGuidelines);
        }
        private static String bounded(String value) {
            if (value == null) return "";
            if (value.length() > 4000) throw new IllegalArgumentException("profile field too long");
            return value.strip();
        }
    }
    public record IngestUrl(@NotBlank @Size(max=2048) String url) {}
}
