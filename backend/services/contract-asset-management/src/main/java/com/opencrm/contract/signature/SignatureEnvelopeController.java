package com.opencrm.contract.signature;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.contract.signature.acl.SignerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DRG-457 / BC-06-04. SendForSignature / VoidEnvelope / GetEnvelopeStatus. API boundary exposes
 * {@link SignatureEnvelopeDto} only (ADR-006) — never {@link SignatureEnvelope}, and never anything from
 * {@code com.opencrm.contract.signature.acl}.
 */
@RestController
@RequestMapping("/api/v1/signature-envelopes")
@RequiredArgsConstructor
@Tag(name = "Contract/Asset Management", description = "E-signature coordination (ACL to an e-sign provider)")
public class SignatureEnvelopeController {

    private final SignatureCoordinationService coordinationService;
    private final SignatureEnvelopeMapper mapper;

    @PostMapping
    @Operation(summary = "SendForSignature")
    public ApiResponse<SignatureEnvelopeDto> sendForSignature(@RequestBody SendForSignatureRequest request) {
        List<SignerRequest> signers = request.signers().stream()
                .map(s -> new SignerRequest(s.signerRef(), s.name(), s.email()))
                .toList();
        SignatureEnvelope envelope = coordinationService.sendForSignature(
                request.aggregateType(), request.aggregateId(), request.documentTitle(), signers);
        return ApiResponse.success(mapper.toDto(envelope));
    }

    @PostMapping("/{envelopeId}/void")
    @Operation(summary = "VoidEnvelope")
    public ApiResponse<SignatureEnvelopeDto> voidEnvelope(@PathVariable String envelopeId,
            @RequestBody VoidEnvelopeRequest request) {
        SignatureEnvelope envelope = coordinationService.voidEnvelope(envelopeId, request.reason());
        return ApiResponse.success(mapper.toDto(envelope));
    }

    @GetMapping("/{envelopeId}")
    @Operation(summary = "GetEnvelopeStatus (poll)")
    public ApiResponse<SignatureEnvelopeDto> getEnvelopeStatus(@PathVariable String envelopeId) {
        SignatureEnvelope envelope = coordinationService.getEnvelopeStatus(envelopeId);
        return ApiResponse.success(mapper.toDto(envelope));
    }
}
