package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.lineitem.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SourceTermsServiceTest {
    final QuoteRepository quotes = mock(QuoteRepository.class);
    final QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
    final OmsQuoteProperties properties = new OmsQuoteProperties();
    final SourceTermsService service = new SourceTermsService(quotes,lines,new ObjectMapper().findAndRegisterModules(),properties);
    final AcceptedSnapshotControllerTest helper = new AcceptedSnapshotControllerTest();
    @Test void rejectsStaleVersionAndAcceptedSourceWithoutWriting() {
        properties.setScopeId("pilot");
        var q = Quote.builder().customerExternalId("a").version(3L).status(QuoteStatus.ACCEPTED).build();
        when(quotes.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(q));
        var auth = helper.auth("oms:snapshot:write",List.of("pilot"),List.of("a"));
        assertThatThrownBy(() -> service.capture(1L,new SourceTermsRequest(2,null,null,null,List.of()),auth)).hasMessageContaining("version");
        assertThatThrownBy(() -> service.capture(1L,new SourceTermsRequest(3,null,null,null,List.of()),auth)).hasMessageContaining("editable");
        verify(quotes,never()).save(any()); verifyNoInteractions(lines);
    }
    @Test void capturesOnlyExactOwnedLinesAndInvalidatesApproval() throws Exception {
        properties.setScopeId("pilot");
        var q = Quote.builder().customerExternalId("a").version(3L).status(QuoteStatus.REJECTED).approvalState(ApprovalState.APPROVED).omsApprovalReference("old").build();
        var line = QuoteLineItem.builder().uuid("line").build();
        when(quotes.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(q));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        var auth = helper.auth("oms:snapshot:write",List.of("pilot"),List.of("a"));
        var request = new SourceTermsRequest(3,"address","tax-ref","config-ref",List.of(new SourceTermsRequest.LineTerms("line","DIGITAL","ONE_TIME","0.00",null,null)));
        service.capture(1L,request,auth);
        assertThat(q.getApprovalState()).isEqualTo(ApprovalState.NONE); assertThat(q.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(q.getOmsApprovalReference()).isNull(); assertThat(line.getMetadata()).contains("DIGITAL");
        verify(quotes).save(q); verify(lines).saveAll(List.of(line));
    }
}
