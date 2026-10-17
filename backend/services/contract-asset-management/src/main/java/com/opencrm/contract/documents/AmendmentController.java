package com.opencrm.contract.documents;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.domain.documents.ActorType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CAM amendment HTTP API. No gateway route is registered and no role is granted.
 * Ownership checks are not delivered, so these endpoints are not released.
 */
@RestController
public class AmendmentController {

    private final AmendmentDocumentService documents;
    private final AmendmentRevisionService revisions;

    public AmendmentController(AmendmentDocumentService documents, AmendmentRevisionService revisions) {
        this.documents = documents;
        this.revisions = revisions;
    }

    @PostMapping("/api/v1/contracts/{contractId}/amendments")
    public ResponseEntity<ApiResponse<AmendmentView>> create(@PathVariable UUID contractId,
                                                             @RequestBody CreateAmendment body,
                                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                             HttpServletRequest request) {
        AmendmentOutcome outcome = documents.createForHttp(contractId, body, context(request, idempotencyKey));
        return respond(outcome, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/amendments/{amendmentId}")
    public ApiResponse<AmendmentView> get(@PathVariable UUID amendmentId, HttpServletRequest request) {
        return ApiResponse.success(documents.getForTenant(amendmentId, context(request, "read")));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}/revisions")
    public ResponseEntity<ApiResponse<AmendmentView>> open(@PathVariable UUID amendmentId,
                                                           @RequestBody OpenRevision body,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           HttpServletRequest request) {
        return respond(revisions.openForHttp(amendmentId, body, context(request, idempotencyKey)), HttpStatus.CREATED);
    }

    @PostMapping("/api/v1/amendments/{amendmentId}/revisions/{revisionNo}/pricing")
    public ApiResponse<AmendmentView> price(@PathVariable UUID amendmentId, @PathVariable int revisionNo,
                                            @RequestBody InstallPricing body,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            HttpServletRequest request) {
        return ApiResponse.success(revisions.installPricing(amendmentId, revisionNo, body, context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:submit")
    public ApiResponse<AmendmentView> submit(@PathVariable UUID amendmentId, @RequestBody VersionBody body,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest request) {
        return ApiResponse.success(documents.submit(amendmentId, body.version(), context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:record-approval")
    public ApiResponse<AmendmentView> approve(@PathVariable UUID amendmentId, @RequestBody ApprovalBody body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest request) {
        return ApiResponse.success(documents.recordApproval(amendmentId, body.version(), body.approvalRef(),
                context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:accept")
    public ApiResponse<AmendmentView> accept(@PathVariable UUID amendmentId, @RequestBody VersionBody body,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest request) {
        return ApiResponse.success(documents.accept(amendmentId, body.version(), context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:execute")
    public ApiResponse<AmendmentView> execute(@PathVariable UUID amendmentId, @RequestBody VersionBody body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest request) {
        return ApiResponse.success(documents.execute(amendmentId, body.version(), context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:withdraw")
    public ApiResponse<AmendmentView> withdraw(@PathVariable UUID amendmentId, @RequestBody VersionBody body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               HttpServletRequest request) {
        return ApiResponse.success(documents.withdraw(amendmentId, body.version(), context(request, idempotencyKey)));
    }

    @PostMapping("/api/v1/amendments/{amendmentId}:send-for-signature")
    public ApiResponse<AmendmentView> sendForSignature(@PathVariable UUID amendmentId, @RequestBody VersionBody body,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       HttpServletRequest request) {
        return ApiResponse.success(documents.sendForSignature(amendmentId, body.version(), context(request, idempotencyKey)));
    }

    private static ResponseEntity<ApiResponse<AmendmentView>> respond(AmendmentOutcome outcome, HttpStatus created) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : created);
        if (outcome.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(ApiResponse.success(outcome.view()));
    }

    private static CommandContext context(HttpServletRequest request, String idempotencyKey) {
        String actorType = request.getHeader("X-Actor-Type");
        return new CommandContext(UUID.fromString(request.getHeader("X-Tenant-Id")), header(request, "X-Caller-Id"),
                idempotencyKey, header(request, "X-Actor-Id"),
                actorType == null || actorType.isBlank() ? ActorType.USER : ActorType.valueOf(actorType),
                header(request, "X-Actor-Role"), header(request, "X-Correlation-Id"));
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(name + " is required", HttpStatus.BAD_REQUEST, "VALIDATION_REJECTED");
        }
        return value;
    }

    public record ApprovalBody(int version, String approvalRef) {
    }
}
