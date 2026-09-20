package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class SourceTermsService {
    private final QuoteRepository quotes;
    private final QuoteLineItemRepository lines;
    private final ObjectMapper mapper;
    private final OmsQuoteProperties properties;
    @Transactional
    public void capture(Long id, SourceTermsRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)
            || !has(jwt, "permissions", "oms:snapshot:write") || properties.getScopeId() == null
            || !has(jwt, "scope_ids", properties.getScopeId())) throw new AccessDeniedException("Source capture denied");
        var quote = quotes.findByIdAndDeletedFalse(id).filter(q -> has(jwt,"account_ids",q.getCustomerExternalId()))
            .orElseThrow(() -> new ResourceNotFoundException("Quote"));
        if (!Objects.equals(quote.getVersion(), request.expectedVersion()))
            throw new BusinessException("Quote version changed", HttpStatus.CONFLICT, "VERSION_CONFLICT");
        if (!Set.of(QuoteStatus.DRAFT, QuoteStatus.REJECTED).contains(quote.getStatus()) || ("OMS".equals(quote.getFulfillmentOwner())
                && (quote.getStatus()!=QuoteStatus.DRAFT || quote.getOmsCorrectionOperationKey()==null || quote.getAcceptedRevision()<1)))
            throw new BusinessException("Source terms require an editable unaccepted quote", HttpStatus.CONFLICT, "QUOTE_NOT_EDITABLE");
        if (request.lines() == null) throw new BusinessException("Source lines required", "INVALID_SOURCE_TERMS");
        var sourceLines = lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        var requested = new HashMap<String, SourceTermsRequest.LineTerms>();
        for (var line : request.lines()) {
            if (line == null || line.sourceLineId() == null || requested.putIfAbsent(line.sourceLineId(), line) != null)
                throw new BusinessException("Duplicate or missing source line", "INVALID_SOURCE_TERMS");
        }
        if (requested.size() != sourceLines.size() || sourceLines.stream().anyMatch(l -> !requested.containsKey(l.getUuid())))
            throw new BusinessException("Source lines must exactly match the quote", "INVALID_SOURCE_TERMS");
        try {
            ObjectNode qm = metadata(quote.getMetadata());
            ObjectNode evidence = mapper.createObjectNode();
            evidence.put("address", request.address()); evidence.put("taxEvidence", request.taxEvidence());
            evidence.put("configurationReference", request.configurationReference());
            // An operator cannot assert an approval reference. Subsequent approval is a separate source event.
            qm.set("oms", evidence); quote.setMetadata(mapper.writeValueAsString(qm));
            for (var source : sourceLines) {
                ObjectNode lm = metadata(source.getMetadata());
                lm.set("oms", mapper.valueToTree(requested.get(source.getUuid())));
                source.setMetadata(mapper.writeValueAsString(lm));
            }
            quote.setOmsApprovalReference(null);
            quote.setApprovalState(ApprovalState.NONE); quote.setStatus(QuoteStatus.DRAFT);
            quotes.save(quote); lines.saveAll(sourceLines);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("Invalid source metadata", "INVALID_SOURCE_TERMS");
        }
    }
    private ObjectNode metadata(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        var node = mapper.readTree(json);
        if (!(node instanceof ObjectNode object)) throw new BusinessException("Source metadata must be an object", "INVALID_SOURCE_TERMS");
        return object;
    }
    private boolean has(Jwt jwt, String claim, String value) {
        Object values = jwt.getClaim(claim);
        return value != null && values instanceof Collection<?> collection && collection.contains(value);
    }
}
