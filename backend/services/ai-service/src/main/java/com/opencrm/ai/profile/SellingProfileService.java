package com.opencrm.ai.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SellingProfileService {
    private final SellingProfileRepository repository;
    private final SafeWebsiteClient websites;
    public SellingProfileService(SellingProfileRepository repository, SafeWebsiteClient websites) {
        this.repository=repository; this.websites=websites;
    }
    @Transactional(readOnly=true)
    public Optional<SellingProfile> get(String tenant) { require(tenant); return repository.find(tenant); }
    @Transactional
    public SellingProfile put(String tenant, SellingProfile.Fields fields, String actor) {
        require(tenant); require(actor);
        if(fields==null) throw new IllegalArgumentException("fields required");
        return repository.save(tenant,fields,actor);
    }
    // Fetch before entering repository work: no database transaction/lock spans outbound IO.
    public SellingProfile ingest(String tenant, String url, String actor) {
        require(tenant); require(actor);
        String summary=websites.fetch(url);
        try {
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(summary.getBytes(StandardCharsets.UTF_8)));
            return repository.saveSummary(tenant,url,summary,sha,actor);
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void require(String value) {
        if(value==null || value.isBlank()) throw new IllegalArgumentException("tenant/actor required");
    }
}
