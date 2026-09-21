package com.opencrm.jev.shadow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcJevDecisionLogStore")
class JdbcJevDecisionLogStoreTest {

    @Mock
    private NamedParameterJdbcOperations jdbc;

    private static JevDecisionRecord row() {
        return new JevDecisionRecord("quote-tier-suggestion", "quote", "42", "tier", "choice",
                "typesafe/jev-1.13", "quote-tier-v1", "http", "NATIVE", "quote-tier", 1, "a".repeat(64),
                "{\"line_count\":3}", "SALES_VP", 0.8, "{\"SALES_VP\":0.8}", null,
                JevDecisionRecord.AuthoritativeSource.NONE, null, "OK", null, 120, 1, null);
    }

    @Test
    @DisplayName("save binds the pinned evaluator config and never a raw payload column")
    void saveBindsRecord() {
        new JdbcJevDecisionLogStore(jdbc).save(row());

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(eq(JdbcJevDecisionLogStore.INSERT), params.capture());
        assertThat(params.getValue().getValue("evaluatorModel")).isEqualTo("typesafe/jev-1.13");
        assertThat(params.getValue().getValue("rubricVersion")).isEqualTo("quote-tier-v1");
        assertThat(params.getValue().getValue("authoritativeSource")).isEqualTo("NONE");
        assertThat(JdbcJevDecisionLogStore.INSERT).doesNotContain("request", "response", "payload");
    }

    @Test
    @DisplayName("a database failure never reaches the caller")
    void neverThrows() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        JdbcJevDecisionLogStore store = new JdbcJevDecisionLogStore(jdbc);

        assertThatCode(() -> store.save(row())).doesNotThrowAnyException();
        assertThat(store.findLatest("quote-tier-suggestion", "hash")).isEmpty();
        assertThat(store.findSubjectsAwaitingOutcome("quote-tier-suggestion", "quote", 10)).isEmpty();
        assertThat(store.recordOutcome("quote-tier-suggestion", "quote", "42", "CFO",
                JevDecisionRecord.AuthoritativeSource.USER)).isZero();
    }

    @Test
    @DisplayName("an outcome is only recorded on rows that have an answer and no outcome yet")
    void recordOutcomeIsGuarded() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(2);

        int updated = new JdbcJevDecisionLogStore(jdbc).recordOutcome("quote-tier-suggestion", "quote", "42",
                "CFO", JevDecisionRecord.AuthoritativeSource.USER);

        assertThat(updated).isEqualTo(2);
        assertThat(JdbcJevDecisionLogStore.RECORD_OUTCOME)
                .contains("authoritative_outcome IS NULL", "answer_value IS NOT NULL",
                        "agreement = (answer_value = :outcome)");
    }

    @Test
    @DisplayName("the no-op store answers reads with nothing")
    void noOpStore() {
        assertThat(JevDecisionLogStore.NONE.findLatest("x", "y")).isEmpty();
        assertThat(JevDecisionLogStore.NONE.findSubjectsAwaitingOutcome("x", "y", 5)).isEqualTo(List.of());
    }
}
