package com.opencrm.ai.ledger;

import com.opencrm.ai.task.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static com.opencrm.ai.ledger.LedgerTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerTaskHandlerTest {
    AgentLedgerService ledger=mock(AgentLedgerService.class);
    AgentTaskHandler handler=mock(AgentTaskHandler.class);
    AgentTask task=new AgentTask();
    UUID id=UUID.randomUUID();
    LedgerTaskHandlerTest() {
        task.setId(UUID.randomUUID()); task.setTenantId("tenant"); when(handler.kind()).thenReturn("enrich");
        when(ledger.start(eq("tenant"),any(),eq("task-dispatcher")))
                .thenReturn(new Run(id,"tenant",task.getId(),Status.RUNNING,"unknown","unknown",0,0,0,
                        BigDecimal.ZERO,Instant.now(),null,null,null));
    }
    @Test void recordsSuccessAndHandlerAction() {
        new LedgerTaskHandler(handler,ledger).handle(task);
        var order=inOrder(ledger,handler);
        order.verify(ledger).start(eq("tenant"),any(),eq("task-dispatcher")); order.verify(handler).handle(task);
        order.verify(ledger).recordAction(eq("tenant"),eq(id),argThat(a -> a.kind().equals("HANDLER") && a.promptTokens()==0),eq("task-handler"));
        order.verify(ledger).complete("tenant",id,"task-dispatcher");
        assertThatThrownBy(() -> LedgerTaskHandler.meter(null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void recordsFailureWithoutPersistingExceptionPayload() {
        RuntimeException failure=new IllegalStateException("sensitive payload"); doThrow(failure).when(handler).handle(task);
        assertThatThrownBy(() -> new LedgerTaskHandler(handler,ledger).handle(task)).isSameAs(failure);
        verify(ledger).fail("tenant",id,"HANDLER_FAILED","task-dispatcher");
        verify(ledger,never()).complete(any(),any(),any());
        assertThatThrownBy(() -> LedgerTaskHandler.meter(null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void reportsActualUsageInCurrentRun() {
        ActionInput usage=new ActionInput("LLM","chat","azure","gpt",100,20,BigDecimal.ONE,BigDecimal.TEN,7);
        doAnswer(call -> { LedgerTaskHandler.meter(usage); return null; }).when(handler).handle(task);
        new LedgerTaskHandler(handler,ledger).handle(task);
        verify(ledger).recordAction("tenant",id,usage,"task-handler");
    }
    @Test void ledgerFailurePreventsUntrackedExecution() {
        when(ledger.start(any(),any(),any())).thenThrow(new IllegalStateException("db unavailable"));
        assertThatThrownBy(() -> new LedgerTaskHandler(handler,ledger).handle(task)).hasMessage("db unavailable");
        verify(handler,never()).handle(any());
    }
    @Test void failureToPersistFailureDoesNotMaskOriginalError() {
        RuntimeException original=new IllegalStateException("handler"); doThrow(original).when(handler).handle(task);
        doThrow(new IllegalStateException("ledger")).when(ledger).fail(any(),any(),any(),any());
        assertThatThrownBy(() -> new LedgerTaskHandler(handler,ledger).handle(task)).isSameAs(original);
        assertThat(original.getSuppressed()).hasSize(1);
    }
}
