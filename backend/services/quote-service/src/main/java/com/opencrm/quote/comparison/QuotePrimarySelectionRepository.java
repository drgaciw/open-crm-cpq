package com.opencrm.quote.comparison;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One preference per workforce partition/opportunity, with PostgreSQL serializing concurrent first writes. */
@Repository
public class QuotePrimarySelectionRepository {
    private final JdbcTemplate jdbc;

    public QuotePrimarySelectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> selectedQuote(String tenant, String deal) {
        return jdbc.query("select selected_quote_id from quote.quote_primary_selections "
                        + "where tenant_id = ? and deal_external_id = ?",
                (row, index) -> row.getLong(1), tenant, deal).stream().findFirst();
    }

    public void select(String tenant, String deal, Long quoteId, String actor) {
        jdbc.update("""
                insert into quote.quote_primary_selections
                    (tenant_id, deal_external_id, selected_quote_id, selected_by, selected_at)
                values (?, ?, ?, ?, CURRENT_TIMESTAMP)
                on conflict (tenant_id, deal_external_id) do update
                    set selected_quote_id = excluded.selected_quote_id,
                        selected_by = excluded.selected_by, selected_at = excluded.selected_at
                """, tenant, deal, quoteId, actor);
    }
}
