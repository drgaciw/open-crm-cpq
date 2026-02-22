package com.opencrm.quote.document;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes/{quoteId}/documents")
@RequiredArgsConstructor
@Tag(name = "Quote Documents", description = "Quote document generation and retrieval")
public class QuoteDocumentController {

    private final QuoteDocumentService documentService;

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a quote document")
    public ApiResponse<QuoteDocumentDto> generate(
            @PathVariable Long quoteId,
            @RequestParam(defaultValue = "PROPOSAL") DocumentType type) {
        QuoteDocumentDto doc = documentService.generateDocument(quoteId, type);
        return ApiResponse.success(doc, "Document generated successfully");
    }

    @GetMapping
    @Operation(summary = "List documents for a quote")
    public ApiResponse<List<QuoteDocumentDto>> list(@PathVariable Long quoteId) {
        List<QuoteDocumentDto> docs = documentService.getDocuments(quoteId);
        return ApiResponse.success(docs);
    }

    @GetMapping("/{documentId}/download")
    @Operation(summary = "Download a quote document")
    public ResponseEntity<byte[]> download(@PathVariable Long quoteId, @PathVariable Long documentId) {
        byte[] content = documentService.getDocumentContent(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
