package com.opencrm.ai.profile;

import com.opencrm.ai.task.AgentTask;
import com.opencrm.ai.task.AgentTaskHandler;

/** Context is ephemeral: never rewrite payload, ledger, or database state. */
public final class ProfileTaskHandler implements AgentTaskHandler {
    private final AgentTaskHandler delegate;
    private final SellingProfileContextProvider context;
    public ProfileTaskHandler(AgentTaskHandler delegate,SellingProfileContextProvider context) {
        this.delegate=delegate; this.context=context;
    }
    public String kind() { return delegate.kind(); }
    public void handle(AgentTask task) {
        task.setSellingProfileContext(context.promptBlock(task.getTenantId()));
        try { delegate.handle(task); }
        finally { task.setSellingProfileContext(""); }
    }
}
