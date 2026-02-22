package com.opencrm.quote.quote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteNumberGenerator {

    private final QuoteRepository quoteRepository;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Retryable(retryFor = DataIntegrityViolationException.class, maxAttempts = 3)
    public synchronized String generate() {
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        String prefix = "Q-" + dateStr + "-";
        int maxSeq = quoteRepository.findMaxSequenceByPrefix(prefix + "%");
        int nextSeq = maxSeq + 1;
        return prefix + String.format("%04d", nextSeq);
    }
}
