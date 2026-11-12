package com.opencrm.ai.profile;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellingProfileRepositoryTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    SellingProfileRepository repository=new SellingProfileRepository(jdbc);
    SellingProfile profile=new SellingProfile(SellingProfileServiceTest.fields(),null,null,null,null,"actor",Instant.now(),1);
    @Test void readRequiresTenantPredicate() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq("a"))).thenReturn(List.of(profile));
        assertEquals(profile,repository.find("a").orElseThrow());
        verify(jdbc).query(eq("SELECT * FROM ai.workspace_selling_profile WHERE tenant_id=?"),any(RowMapper.class),eq("a"));
    }
    @Test void profileUpsertUsesTenantConflictAndAtomicVersion() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq("a"),eq("Company"),eq("Product"),eq("Value"),eq("ICP"),eq("Competition"),eq("No discounts"),eq("Concise"),eq("actor"))).thenReturn(List.of(profile));
        assertEquals(profile,repository.save("a",SellingProfileServiceTest.fields(),"actor"));
        verify(jdbc).query(argThat(sql->sql.contains("ON CONFLICT (tenant_id)") && sql.contains("version=ai.workspace_selling_profile.version+1")
            && !sql.contains("company_url=")),any(RowMapper.class),eq("a"),eq("Company"),eq("Product"),eq("Value"),eq("ICP"),eq("Competition"),eq("No discounts"),eq("Concise"),eq("actor"));
    }
    @Test void urlUpsertDoesNotOverwriteStructuredFields() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq("a"),eq("url"),eq("summary"),eq("sha"),eq("actor"))).thenReturn(List.of(profile));
        assertEquals(profile,repository.saveSummary("a","url","summary","sha","actor"));
        verify(jdbc).query(argThat(sql->sql.contains("ON CONFLICT (tenant_id)") && sql.contains("fetched_at=EXCLUDED.fetched_at")
            && !sql.contains("company_overview=")),any(RowMapper.class),eq("a"),eq("url"),eq("summary"),eq("sha"),eq("actor"));
    }
    @Test void mapperPreservesVersionAndProvenance() throws Exception {
        ResultSet r=mock(ResultSet.class); Instant now=Instant.now();
        when(r.getString("company_overview")).thenReturn("Company");
        when(r.getString("updated_by")).thenReturn("actor"); when(r.getString("summary_sha256")).thenReturn("sha");
        when(r.getTimestamp("updated_at")).thenReturn(Timestamp.from(now)); when(r.getLong("version")).thenReturn(7L);
        var mapped=SellingProfileRepository.MAPPER.mapRow(r,0);
        assertEquals(7,mapped.version()); assertEquals("sha",mapped.summarySha256()); assertEquals(now,mapped.updatedAt());
        assertNull(mapped.fetchedAt()); assertEquals("Company",mapped.fields().companyOverview());
    }
}
