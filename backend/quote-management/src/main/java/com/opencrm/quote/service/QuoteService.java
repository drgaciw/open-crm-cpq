package com.opencrm.quote.service;

import com.opencrm.quote.domain.Quote;
import com.opencrm.quote.domain.QuoteStatus;
import com.opencrm.quote.repository.QuoteRepository;
import com.opencrm.quote.repository.OutboxEventRepository;
import com.opencrm.shared.outbox.OutboxEvent;
import com.opencrm.shared.kafka.KafkaTopics;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Quote createQuote(Quote quote) {
        quote.calculateTotal();
        return quoteRepository.save(quote);
    }

    @Transactional
    public Quote acceptQuote(Long id) {
        Quote quote = quoteRepository.findById(id).orElseThrow(() -> new RuntimeException("Quote not found"));
        
        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            return quote;
        }
        
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(Instant.now());
        quoteRepository.save(quote);

        // Publish event via Outbox
        String payload = String.format("{\"quote_id\": \"%s\", \"accepted_at\": \"%s\", \"customer_id\": \"%s\", \"total_amount\": \"%s\"}", 
            id, quote.getAcceptedAt(), quote.getCustomerId(), quote.getTotalAmount().getAmount());
            
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Quote")
            .aggregateId(id.toString())
            .type(KafkaTopics.QUOTE_STATUS_ACCEPTED)
            .payload(payload)
            .build();
            
        outboxEventRepository.save(event);
        
        return quote;
    }
}
