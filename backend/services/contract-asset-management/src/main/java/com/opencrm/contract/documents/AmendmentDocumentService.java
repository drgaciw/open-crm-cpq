package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AmendmentDocumentService {

    private final AmendmentWorkflow workflow;

    public AmendmentDocumentService(AmendmentWorkflow workflow) {
        this.workflow = workflow;
    }

    public AmendmentView create(UUID contractId, CreateAmendment command, CommandContext context) {
        return workflow.create(contractId, command, context).view();
    }

    public AmendmentOutcome createForHttp(UUID contractId, CreateAmendment command, CommandContext context) {
        return workflow.create(contractId, command, context);
    }

    public AmendmentView get(UUID id) {
        return workflow.get(id);
    }

    public AmendmentView getForTenant(UUID id, CommandContext context) {
        return workflow.getForTenant(id, context);
    }

    public AmendmentView submit(UUID id, int version, CommandContext context) {
        return workflow.submit(id, version, context);
    }

    public AmendmentView recordApproval(UUID id, int version, String approvalRef, CommandContext context) {
        return workflow.recordApproval(id, version, approvalRef, context);
    }

    public AmendmentView accept(UUID id, int version, CommandContext context) {
        return workflow.accept(id, version, context);
    }

    public AmendmentView execute(UUID id, int version, CommandContext context) {
        return workflow.execute(id, version, context);
    }

    public AmendmentView withdraw(UUID id, int version, CommandContext context) {
        return workflow.withdraw(id, version, context);
    }

    public AmendmentView rejectReview(UUID id, int version, CommandContext context) {
        return workflow.rejectReview(id, version, context);
    }

    public AmendmentView sendForSignature(UUID id, int version, CommandContext context) {
        return workflow.sendForSignature(id, version, context);
    }

    public Transition applyTransition(AmendmentDocumentStatus current, AmendmentDocumentStatus outcome) {
        return workflow.applyTransition(current, outcome);
    }
}
