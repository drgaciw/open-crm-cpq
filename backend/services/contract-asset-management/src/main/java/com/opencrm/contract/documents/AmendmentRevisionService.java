package com.opencrm.contract.documents;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AmendmentRevisionService {

    private final AmendmentWorkflow workflow;

    public AmendmentRevisionService(AmendmentWorkflow workflow) {
        this.workflow = workflow;
    }

    public AmendmentView open(UUID id, OpenRevision command, CommandContext context) {
        return workflow.open(id, command, context).view();
    }

    public AmendmentOutcome openForHttp(UUID id, OpenRevision command, CommandContext context) {
        return workflow.open(id, command, context);
    }

    public AmendmentView installPricing(UUID id, int revisionNo, InstallPricing command, CommandContext context) {
        return workflow.installPricing(id, revisionNo, command, context);
    }
}
