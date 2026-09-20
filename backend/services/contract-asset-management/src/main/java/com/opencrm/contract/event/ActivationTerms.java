package com.opencrm.contract.event;
import com.opencrm.common.order.ActivationRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class ActivationTerms {
    private ActivationTerms() {}
    static BigDecimal arr(ActivationRequest r) {
        if (r == null || r.line() == null || r.acceptedRevision() < 1 || r.completedAt() == null) throw invalid();
        for (String s : new String[]{r.eventId(),r.operationKey(),r.orderId(),r.scopeId(),r.customerId(),r.quoteId(),r.evidenceReference(),r.line().sourceLineId(),r.line().productId(),r.line().description()}) {
            if (s == null || s.isBlank()) throw invalid();
        }
        var l = r.line();
        if (!"RECURRING".equals(l.chargeType()) || l.quantity() <= 0 || l.startDate() == null || l.endDate() == null || !l.endDate().isAfter(l.startDate())) throw invalid();
        BigDecimal net = new BigDecimal(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity())).subtract(new BigDecimal(l.discount())).setScale(2, RoundingMode.UNNECESSARY);
        if (net.signum() < 0 || net.add(new BigDecimal(l.tax())).compareTo(new BigDecimal(l.total())) != 0) throw invalid();
        return switch(l.billingFrequency()) {
            case "MONTHLY" -> net.multiply(BigDecimal.valueOf(12));
            case "ANNUAL" -> net;
            default -> throw invalid();
        };
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Verified recurring line terms and evidence required"); }
}
