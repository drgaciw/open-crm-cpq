package com.opencrm.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Durable submit-only receipts. The receipt and canonical approval commit in one routing transaction. */
@Service
@RequiredArgsConstructor
public class IdempotentApprovalSubmission {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private final JdbcTemplate jdbc;
    private final ApprovalService approvals;
    private final CurrentActor actor;

    @Transactional
    public ApprovalRequestDto submit(String key, ApprovalSubmitRequest request) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new BusinessException("Invalid Idempotency-Key", HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
        }
        String keyHash = digest(key);
        String payload = encode(request);
        // The verified requester is part of the binding: a different caller cannot replay another user's response.
        String fingerprint = digest(encode(actor.id()) + ":" + payload);
        // PostgreSQL waits on an uncommitted conflicting insert. A rollback frees the key for the next attempt.
        jdbc.update("INSERT INTO approval_submit_receipts (key_hash, request_hash) VALUES (?, ?) ON CONFLICT DO NOTHING",
                keyHash, fingerprint);
        Receipt receipt = jdbc.queryForObject("SELECT request_hash, response_json FROM approval_submit_receipts WHERE key_hash = ? FOR UPDATE",
                (rs, row) -> new Receipt(rs.getString(1), rs.getString(2)), keyHash);
        if (receipt == null || !fingerprint.equals(receipt.requestHash())) {
            throw new BusinessException("Idempotency key was used for a different approval command",
                    HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
        }
        if (receipt.response() != null) return decode(receipt.response());
        ApprovalRequestDto result = approvals.submit(request);
        jdbc.update("UPDATE approval_submit_receipts SET response_json = ? WHERE key_hash = ?", encode(result), keyHash);
        return result;
    }

    private static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize approval receipt", ex); }
    }

    private static ApprovalRequestDto decode(String value) {
        try { return JSON.readValue(value, ApprovalRequestDto.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to read approval receipt", ex); }
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private record Receipt(String requestHash, String response) { }
}
