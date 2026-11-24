package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class QuoteNumberGenerator {
    private static final int MAX_NUMBER_LENGTH = 50;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    // Include soft-deleted and externally imported numeric history. The conflicting row's latest
    // value is read under PostgreSQL's row lock, even when the history snapshot predates its commit.
    private static final String ALLOCATE = """
            INSERT INTO quote.quote_number_counters AS counter (quote_day, last_number)
            SELECT ?, COALESCE(MAX(SUBSTRING(quote_number FROM 12)::numeric), 0) + 1
            FROM quote.quotes
            WHERE quote_number LIKE ? AND SUBSTRING(quote_number FROM 12) ~ '^[0-9]+$'
            ON CONFLICT (quote_day) DO UPDATE
            SET last_number = GREATEST(counter.last_number + 1, EXCLUDED.last_number)
            RETURNING last_number
            """;

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String generate() {
        return generate(LocalDate.now());
    }

    String generate(LocalDate day) {
        String prefix = "Q-" + day.format(DATE_FORMAT) + "-";
        BigDecimal sequence = jdbc.queryForObject(ALLOCATE, BigDecimal.class, day, prefix + "%");
        String number = prefix + String.format(Locale.ROOT, "%04d", sequence.toBigIntegerExact());
        if (number.length() > MAX_NUMBER_LENGTH) {
            throw new BusinessException("Quote numbers are exhausted for " + day,
                    HttpStatus.CONFLICT, "QUOTE_NUMBER_EXHAUSTED");
        }
        return number;
    }
}
