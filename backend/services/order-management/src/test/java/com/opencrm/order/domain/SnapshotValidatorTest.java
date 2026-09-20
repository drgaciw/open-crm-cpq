package com.opencrm.order.domain;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.common.order.AcceptedOrderSnapshot.Line;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SnapshotValidatorTest {
 static AcceptedOrderSnapshot snapshot(String total, List<Line> lines) { return new AcceptedOrderSnapshot(1,"snap-1","00000000-0000-0000-0000-000000000001","Q-1",1,"pilot","customer",Instant.now(),"USD","30.00","0","0",total,"address","approved","tax","configured",lines); }
 static Line line() { return new Line("line-1","product","Widget","PHYSICAL","ONE_TIME",3,"EA","10.00","0","0","30.00","ONE_TIME",null,null); }
 @Test void rejectsUnreconciledHeader() { assertThrows(RuntimeException.class,()->new SnapshotValidator().validate(snapshot("29.99",List.of(line())))); }
 @Test void rejectsDuplicateLines() { assertThrows(RuntimeException.class,()->new SnapshotValidator().validate(snapshot("30",List.of(line(),line())))); }
 @Test void exactDecimalPasses() { assertTrue(new SnapshotValidator().validate(snapshot("30.00",List.of(line()))).isEmpty()); }
 @Test void missingEvidenceHolds() { var s=snapshot("30",List.of(line())); var missing=new AcceptedOrderSnapshot(1,s.snapshotId(),s.quoteId(),s.quoteNumber(),1,s.scopeId(),s.customerId(),s.acceptedAt(),"USD","30","0","0","30",null,null,null,null,s.lines()); assertEquals(4,new SnapshotValidator().validate(missing).size()); }

 @Test void rejectsUsdPrecisionBeyondCents() {var l=new Line("line-1","product","Widget","PHYSICAL","ONE_TIME",3,"EA","10.0001","0","0","30.0003","ONE_TIME",null,null);var o=snapshot("30.0003",List.of(l));var precise=new AcceptedOrderSnapshot(1,o.snapshotId(),o.quoteId(),o.quoteNumber(),1,o.scopeId(),o.customerId(),o.acceptedAt(),"USD","30.0003","0","0","30.0003",o.address(),o.approvalReference(),o.taxEvidence(),o.configurationReference(),o.lines());assertThrows(RuntimeException.class,()->new SnapshotValidator().validate(precise));}
 @Test void equalRecurringDatesAreInvalid() {var l=new Line("line-1","product","Widget","DIGITAL","RECURRING",3,"EA","10","0","0","30","MONTHLY",LocalDate.of(2026,1,1),LocalDate.of(2026,1,1));assertThrows(RuntimeException.class,()->new SnapshotValidator().validate(snapshot("30",List.of(l))));}
 @Test void missingMappingCreatesExplicitHold() {var l=new Line("line-1","product","Widget",null,"ONE_TIME",3,"EA","10","0","0","30","ONE_TIME",null,null);assertTrue(new SnapshotValidator().validate(snapshot("30",List.of(l))).contains("MAPPING:line-1"));}
}
