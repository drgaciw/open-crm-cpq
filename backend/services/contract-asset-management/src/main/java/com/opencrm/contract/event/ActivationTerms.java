package com.opencrm.contract.event;
import com.opencrm.common.order.ActivationRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

final class ActivationTerms {
    private ActivationTerms() {}
    static BigDecimal arr(ActivationRequest r) {
        if (r == null || r.line() == null || r.acceptedRevision() < 1 || r.completedAt() == null) throw invalid();
        for (String s : new String[]{r.eventId(),r.operationKey(),r.orderId(),r.scopeId(),r.customerId(),r.quoteId(),r.evidenceReference(),r.line().sourceLineId(),r.line().productId(),r.line().description(),r.line().uom()}) {
            if (s == null || s.isBlank() || s.length()>255) throw invalid();
        }
        try { if (!UUID.fromString(r.quoteId()).toString().equals(r.quoteId())) throw invalid(); }
        catch (RuntimeException e) { throw invalid(); }
        var l = r.line();
        if (!Set.of("PHYSICAL","DIGITAL","SERVICE").contains(l.fulfillmentType()==null?"":l.fulfillmentType())
            || !"RECURRING".equals(l.chargeType()) || l.quantity() <= 0 || l.startDate() == null || l.endDate() == null || !l.endDate().isAfter(l.startDate())) throw invalid();
        BigDecimal gross=amount(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity()));
        BigDecimal discount=amount(l.discount());
        if(discount.compareTo(gross)>0) throw invalid();
        BigDecimal net=gross.subtract(discount);
        if(net.add(amount(l.tax())).compareTo(amount(l.total()))!=0) throw invalid();
        BigDecimal arr=switch(l.billingFrequency()==null?"":l.billingFrequency()) {
            case "MONTHLY" -> net.multiply(BigDecimal.valueOf(12));
            case "ANNUAL" -> net;
            default -> throw invalid();
        };
        if(arr.precision()-arr.scale()>17) throw invalid();
        return arr.setScale(2);
    }
    private static BigDecimal amount(String value) {
        if(value==null || !value.matches("[0-9]{1,14}(\\.[0-9]{1,2})?")) throw invalid();
        return new BigDecimal(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Verified recurring line terms and evidence required"); }
}
