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
}
