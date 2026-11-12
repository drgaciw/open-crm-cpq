package com.opencrm.ai.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class SellingProfileRepository {
    private final JdbcTemplate jdbc;
    public SellingProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    static final RowMapper<SellingProfile> MAPPER = (r,n) -> new SellingProfile(new SellingProfile.Fields(
            r.getString("company_overview"), r.getString("product_overview"), r.getString("value_propositions"),
            r.getString("target_segments"), r.getString("differentiators_competitors"),
            r.getString("commercial_guardrails"), r.getString("tone_guidelines")), r.getString("company_url"),
            r.getString("fetched_summary"), instant(r.getTimestamp("fetched_at")), r.getString("summary_sha256"),
            r.getString("updated_by"), instant(r.getTimestamp("updated_at")), r.getLong("version"));
    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }
    public Optional<SellingProfile> find(String tenant) {
        return jdbc.query("SELECT * FROM ai.workspace_selling_profile WHERE tenant_id=?", MAPPER, tenant).stream().findFirst();
    }
    public SellingProfile save(String tenant, SellingProfile.Fields f, String actor) {
        return jdbc.query("""
            INSERT INTO ai.workspace_selling_profile(tenant_id,company_overview,product_overview,value_propositions,
                target_segments,differentiators_competitors,commercial_guardrails,tone_guidelines,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id) DO UPDATE SET
                company_overview=EXCLUDED.company_overview,product_overview=EXCLUDED.product_overview,
                value_propositions=EXCLUDED.value_propositions,target_segments=EXCLUDED.target_segments,
                differentiators_competitors=EXCLUDED.differentiators_competitors,
                commercial_guardrails=EXCLUDED.commercial_guardrails,tone_guidelines=EXCLUDED.tone_guidelines,
                updated_by=EXCLUDED.updated_by,updated_at=now(),version=ai.workspace_selling_profile.version+1
            RETURNING *
            """, MAPPER, tenant,f.companyOverview(),f.productOverview(),f.valuePropositions(),f.targetSegments(),
                f.differentiatorsCompetitors(),f.commercialGuardrails(),f.toneGuidelines(),actor).getFirst();
    }
    public SellingProfile saveSummary(String tenant, String url, String summary, String sha, String actor) {
        return jdbc.query("""
            INSERT INTO ai.workspace_selling_profile(tenant_id,company_url,fetched_summary,fetched_at,summary_sha256,updated_by)
            VALUES (?,?,?,now(),?,?) ON CONFLICT (tenant_id) DO UPDATE SET
                company_url=EXCLUDED.company_url,fetched_summary=EXCLUDED.fetched_summary,fetched_at=EXCLUDED.fetched_at,
                summary_sha256=EXCLUDED.summary_sha256,updated_by=EXCLUDED.updated_by,updated_at=now(),
                version=ai.workspace_selling_profile.version+1 RETURNING *
            """, MAPPER,tenant,url,summary,sha,actor).getFirst();
    }
}
