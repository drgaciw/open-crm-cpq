package com.opencrm.dealdesk.model.tcv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContingentRevenue {

    private String description;
    private BigDecimal amount;
    private LocalDate triggerDate;
    private String triggerCondition;
    private boolean achieved;
    private LocalDate achievedDate;
    private String verificationMethod;
    private String verificationStatus;
    private String verificationNotes;

    @Builder.Default
    private boolean includedInTCV = false;

    public void validate() {
        Assert.notNull(amount, "Amount cannot be null");
        Assert.isTrue(amount.compareTo(BigDecimal.ZERO) > 0, "Amount must be greater than zero");
        Assert.notNull(triggerDate, "Trigger date is required");

        if (achieved) {
            Assert.notNull(achievedDate, "Achieved date is required when revenue is achieved");
            Assert.isTrue(!achievedDate.isBefore(triggerDate), "Achieved date cannot be before trigger date");
        }
    }

    public boolean isVerified() {
        return "VERIFIED".equalsIgnoreCase(verificationStatus);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(verificationStatus);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(verificationStatus);
    }
}
