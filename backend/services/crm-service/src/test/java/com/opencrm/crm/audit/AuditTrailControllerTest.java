package com.opencrm.crm.audit;

import com.opencrm.audit.AuditTrailService;
import com.opencrm.audit.dto.AuditEntryDto;
import com.opencrm.audit.dto.SearchAuditTrailQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditTrailControllerTest {

    @Mock
    private AuditTrailService auditTrailService;

    @InjectMocks
    private AuditTrailController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/audit-entries returns matching entries in ApiResponse, with pagination")
    void searchReturnsMatchingEntries() throws Exception {
        AuditEntryDto entry = AuditEntryDto.builder()
                .id(1L)
                .tenantId("tenant-1")
                .aggregateType("Lead")
                .aggregateId("42")
                .principal("user:abc")
                .action("CREATE")
                .previousHash("0".repeat(64))
                .entryHash("a".repeat(64))
                .occurredAt(Instant.parse("2026-11-11T10:00:00Z"))
                .recordedAt(Instant.parse("2026-11-11T10:00:01Z"))
                .build();
        when(auditTrailService.search(any(SearchAuditTrailQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .param("tenantId", "tenant-1")
                        .param("aggregateType", "Lead")
                        .param("aggregateId", "42")
                        .param("principal", "user:abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].aggregateType").value("Lead"))
                .andExpect(jsonPath("$.data[0].aggregateId").value("42"))
                .andExpect(jsonPath("$.data[0].entryHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        ArgumentCaptor<SearchAuditTrailQuery> captor = ArgumentCaptor.forClass(SearchAuditTrailQuery.class);
        verify(auditTrailService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().aggregateType()).isEqualTo("Lead");
        assertThat(captor.getValue().aggregateId()).isEqualTo("42");
        assertThat(captor.getValue().principal()).isEqualTo("user:abc");
    }

    @Test
    @DisplayName("GET /api/v1/audit-entries with a time range passes it through to the service")
    void searchPassesTheTimeRangeThrough() throws Exception {
        Page<AuditEntryDto> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(auditTrailService.search(any(SearchAuditTrailQuery.class), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/v1/audit-entries")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        ArgumentCaptor<SearchAuditTrailQuery> captor = ArgumentCaptor.forClass(SearchAuditTrailQuery.class);
        verify(auditTrailService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(captor.getValue().to()).isEqualTo(Instant.parse("2026-12-31T00:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/audit-entries with no filters searches unfiltered")
    void searchWithNoFiltersPassesAllNulls() throws Exception {
        when(auditTrailService.search(any(SearchAuditTrailQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/audit-entries")).andExpect(status().isOk());

        ArgumentCaptor<SearchAuditTrailQuery> captor = ArgumentCaptor.forClass(SearchAuditTrailQuery.class);
        verify(auditTrailService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().tenantId()).isNull();
        assertThat(captor.getValue().aggregateType()).isNull();
        assertThat(captor.getValue().aggregateId()).isNull();
        assertThat(captor.getValue().principal()).isNull();
        assertThat(captor.getValue().from()).isNull();
        assertThat(captor.getValue().to()).isNull();
    }
}
