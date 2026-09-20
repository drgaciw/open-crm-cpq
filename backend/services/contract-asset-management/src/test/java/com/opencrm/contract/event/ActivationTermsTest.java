package com.opencrm.contract.event;
import com.opencrm.common.order.AcceptedOrderSnapshot.Line;
import com.opencrm.common.order.ActivationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActivationTermsTest {
    private ActivationRequest request(String frequency,String charge, LocalDate start) {
        return new ActivationRequest("event","op","order","pilot","account","b70be08b-f70d-406f-82db-96c3f53410dc",1,
            new Line("line","product","Plan","DIGITAL",charge,2,"EA","10.00","2.00","1.80","19.80",frequency,start,LocalDate.of(2027,9,20)),"provision-proof",Instant.parse("2026-09-20T12:00:00Z"));
    }
    @Test void derivesRecurringEconomicsFromNetLineChargeNotOrderOrTax() {
        assertEquals(new BigDecimal("216.00"), ActivationTerms.arr(request("MONTHLY","RECURRING",LocalDate.of(2026,9,20))));
        assertEquals(new BigDecimal("18.00"), ActivationTerms.arr(request("ANNUAL","RECURRING",LocalDate.of(2026,9,20))));
    }
    @Test void rejectsNegativeCommercialAmounts() {
        var r=request("MONTHLY","RECURRING",LocalDate.of(2026,9,20));
        var l=new Line("line","product","Plan","DIGITAL","RECURRING",1,"EA","10.00","-5.00","-1.00","14.00","MONTHLY",LocalDate.of(2026,9,20),LocalDate.of(2027,9,20));
        assertThrows(IllegalArgumentException.class, () -> ActivationTerms.arr(new ActivationRequest(r.eventId(),r.operationKey(),r.orderId(),r.scopeId(),r.customerId(),r.quoteId(),1,l,r.evidenceReference(),r.completedAt())));
    }
    @Test void rejectsExponentPrecisionNoncanonicalIdentityAndEqualDates() {
        var r=request("MONTHLY","RECURRING",LocalDate.of(2026,9,20));
        for(String amount:new String[]{"1e1","10.001","-10.00","NaN","999999999999999.00"}) {
            var l=new Line("line","product","Plan","DIGITAL","RECURRING",1,"EA",amount,"0.00","0.00",amount,"MONTHLY",LocalDate.of(2026,9,20),LocalDate.of(2027,9,20));
            assertThrows(IllegalArgumentException.class,()->ActivationTerms.arr(new ActivationRequest(r.eventId(),r.operationKey(),r.orderId(),r.scopeId(),r.customerId(),r.quoteId(),1,l,r.evidenceReference(),r.completedAt())));
        }
        assertThrows(IllegalArgumentException.class,()->ActivationTerms.arr(new ActivationRequest(r.eventId(),r.operationKey(),r.orderId(),r.scopeId(),r.customerId(),"Q-ALIAS",1,r.line(),r.evidenceReference(),r.completedAt())));
        assertThrows(IllegalArgumentException.class,()->ActivationTerms.arr(request("MONTHLY","RECURRING",LocalDate.of(2027,9,20))));
    }
    @Test void rejectsOneTimeAndMissingSourceDates() {
        assertThrows(IllegalArgumentException.class, () -> ActivationTerms.arr(request("MONTHLY","ONE_TIME",LocalDate.of(2026,9,20))));
        assertThrows(IllegalArgumentException.class, () -> ActivationTerms.arr(request("MONTHLY","RECURRING",null)));
    }
}
