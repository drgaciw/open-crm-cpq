package com.opencrm.quote.document;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteDocumentService {

    private final QuoteDocumentRepository documentRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteLineItemRepository lineItemRepository;
    private final TemplateEngine templateEngine;

    public QuoteDocumentDto generateDocument(Long quoteId, DocumentType documentType) {
        log.debug("Generating {} document for quote {}", documentType, quoteId);

        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        List<QuoteLineItem> lineItems = lineItemRepository
                .findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quoteId);

        Context ctx = new Context();
        ctx.setVariable("quote", quote);
        ctx.setVariable("lineItems", lineItems);

        String html = templateEngine.process("quote-template", ctx);
        byte[] pdfBytes;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            pdfBytes = os.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate PDF for quote {}", quote.getQuoteNumber(), e);
            throw new RuntimeException("PDF generation failed", e);
        }

        String fileName = quote.getQuoteNumber() + "-" + documentType.name().toLowerCase() + ".pdf";

        QuoteDocument doc = QuoteDocument.builder()
                .quoteId(quoteId)
                .documentType(documentType)
                .fileName(fileName)
                .content(pdfBytes)
                .fileSize((long) pdfBytes.length)
                .generatedBy("system")
                .build();

        QuoteDocument saved = documentRepository.save(doc);
        log.info("Generated document {} for quote {}", fileName, quote.getQuoteNumber());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<QuoteDocumentDto> getDocuments(Long quoteId) {
        return documentRepository.findByQuoteIdAndDeletedFalse(quoteId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentContent(Long documentId) {
        QuoteDocument doc = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteDocument", documentId));
        return doc.getContent();
    }

    private QuoteDocumentDto toDto(QuoteDocument doc) {
        return QuoteDocumentDto.builder()
                .id(doc.getId())
                .uuid(doc.getUuid())
                .quoteId(doc.getQuoteId())
                .documentType(doc.getDocumentType())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .generatedBy(doc.getGeneratedBy())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
