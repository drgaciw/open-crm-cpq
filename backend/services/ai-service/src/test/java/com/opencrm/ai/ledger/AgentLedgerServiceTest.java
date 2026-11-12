package com.opencrm.ai.ledger;

import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.opencrm.ai.ledger.LedgerTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentLedgerServiceTest {
    AgentLedgerRepository repo = mock(AgentLedgerRepository.class);
    UUID id=UUID.randomUUID(), task=UUID.randomUUID();
    Instant now=Instant.parse("2026-11-01T12:00:00Z");
    AgentLedgerService service = new AgentLedgerService(repo,Clock.fixed(now,ZoneOffset.UTC),
            Validation.buildDefaultValidatorFactory().getValidator());
    Run run(Status status) { return new Run(id,"tenant",task,status,"azure","gpt",0,0,0,BigDecimal.ZERO,
            now.minusSeconds(2),null,null,null); }
    ActionInput input(long prompt,long completion) { return new ActionInput("LLM","chat","azure","gpt",prompt,completion,
            new BigDecimal("0.125"),new BigDecimal("0.375"),12); }
    @Test void startsWithTaskCorrelationAndAudit() {
        when(repo.taskExists("tenant",task)).thenReturn(true);
        Run result=service.start("tenant",new Start(task,"azure","gpt"),"user");
        assertThat(result.status()).isEqualTo(Status.RUNNING);
        assertThat(result.costUsd()).isEqualByComparingTo("0");
        verify(repo).insert(result);
        var audit=ArgumentCaptor.forClass(Audit.class); verify(repo).append(eq("tenant"),audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo("RUN_STARTED");
    }
    @Test void refusesCrossTenantTask() {
        assertThatThrownBy(() -> service.start("other",new Start(task,"azure","gpt"),"user"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(repo,never()).insert(any());
    }
    @Test void completesLockedRunWithMeasuredLatency() {
        when(repo.find("tenant",id,true)).thenReturn(Optional.of(run(Status.RUNNING)));
        service.complete("tenant",id,"user");
        verify(repo).finish("tenant",id,Status.SUCCEEDED,now,2000,null);
        verify(repo).append(eq("tenant"),argThat(a -> a.eventType().equals("RUN_SUCCEEDED")));
    }
    @Test void failsWithSafeErrorCode() {
        when(repo.find("tenant",id,true)).thenReturn(Optional.of(run(Status.RUNNING)));
        service.fail("tenant",id,"PROVIDER_TIMEOUT","user");
        verify(repo).finish("tenant",id,Status.FAILED,now,2000,"PROVIDER_TIMEOUT");
    }
    @Test void rejectsSensitiveFreeTextError() {
        assertThatThrownBy(() -> service.fail("tenant",id,"customer payload secret", "user"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }
    @ParameterizedTest @EnumSource(value=Status.class,names={"SUCCEEDED","FAILED"})
    void terminalRunsRejectActionsAndTransitions(Status status) {
        when(repo.find("tenant",id,true)).thenReturn(Optional.of(run(status)));
        assertThatThrownBy(() -> service.complete("tenant",id,"user")).hasMessageContaining("409");
        assertThatThrownBy(() -> service.fail("tenant",id,"ERROR","user")).hasMessageContaining("409");
        assertThatThrownBy(() -> service.recordAction("tenant",id,input(1,2),"user")).hasMessageContaining("409");
        verify(repo,never()).insertAction(any(),any());
    }
    @Test void preciseMeteringIsPersistedAndAudited() {
        when(repo.find("tenant",id,true)).thenReturn(Optional.of(run(Status.RUNNING)));
        Action action=service.recordAction("tenant",id,input(3,7),"user");
        assertThat(action.costUsd()).isEqualByComparingTo("0.000003");
        verify(repo).insertAction("tenant",action);
        verify(repo).append(eq("tenant"),argThat(a -> a.eventType().equals("ACTION_RECORDED")));
    }
    @Test void preservesSubMicroDollarPrecision() {
        ActionInput input=new ActionInput("LLM","chat","p","m",1,0,new BigDecimal("0.0000015"),BigDecimal.ZERO,0);
        assertThat(input.cost()).isEqualByComparingTo("0.000000000002");
    }
    @Test void rejectsNegativeUsageAndRates() {
        assertThatThrownBy(() -> input(-1,0).cost()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordAction("tenant",id,input(-1,0),"user")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }
    @Test void overflowIsNotSilentlyWrapped() {
        assertThatThrownBy(() -> input(Long.MAX_VALUE,1).cost()).isInstanceOf(ArithmeticException.class);
    }
    @Test void auditsAreAppendOnlyEvenAfterFinish() {
        when(repo.find("tenant",id,true)).thenReturn(Optional.of(run(Status.SUCCEEDED)));
        Audit audit=service.audit("tenant",id,"REVIEWED","user");
        verify(repo).append("tenant",audit);
        assertThat(Audit.class.isRecord()).isTrue(); assertThat(Action.class.isRecord()).isTrue();
        assertThat(Arrays.stream(AgentLedgerRepository.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("delete","updateAction","updateAudit");
    }
    @Test void foreignTenantCannotReadOrAppend() {
        when(repo.find("other",id,true)).thenReturn(Optional.empty());
        when(repo.find("other",id,false)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("other",id)).hasMessageContaining("404");
        assertThatThrownBy(() -> service.actions("other",id)).hasMessageContaining("404");
        assertThatThrownBy(() -> service.audits("other",id)).hasMessageContaining("404");
        assertThatThrownBy(() -> service.audit("other",id,"REVIEWED","user")).hasMessageContaining("404");
        verify(repo,never()).append(any(),any());
    }
    @Test void delegatesTenantAndTaskRollupsWithoutRounding() {
        Usage total=new Usage(3,10,20,new BigDecimal("0.000000000003"));
        when(repo.usage("tenant",null)).thenReturn(total); when(repo.usage("tenant",task)).thenReturn(total);
        assertThat(service.usage("tenant",null)).isEqualTo(total);
        assertThat(service.usage("tenant",task)).isEqualTo(total);
        verify(repo).usage("tenant",task);
    }
    @Test void nullTenantFailsClosed() {
        assertThatThrownBy(() -> service.usage(null,null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }
}
