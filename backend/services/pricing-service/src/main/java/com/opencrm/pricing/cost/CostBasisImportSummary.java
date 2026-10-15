package com.opencrm.pricing.cost;

import java.time.Instant;

/**
 * The audit entry of one cost export ingestion (ADR-006 DTO). {@code received = accepted + rejected} and
 * {@code accepted = inserted + unchanged + superseded}.
 *
 * @param importId      id of the audit entry
 * @param exportVersion the export's {@code version}, or null when no row carried a parseable one
 * @param approvedBy    the export's Finance approver, or null when no row carried one
 * @param ingestedBy    the verified principal who ingested it: Entra {@code oid}, or {@code service:<client-id>}
 * @param ingestedAt    when
 * @param contentSha256 SHA-256 of the file as received, hex
 * @param unitUnchecked accepted rows whose product states no billing unit in the catalog
 */
public record CostBasisImportSummary(
        Long importId,
        Long exportVersion,
        String approvedBy,
        String ingestedBy,
        Instant ingestedAt,
        String contentSha256,
        int received,
        int accepted,
        int inserted,
        int unchanged,
        int superseded,
        int rejected,
        int unitUnchecked) {
}
