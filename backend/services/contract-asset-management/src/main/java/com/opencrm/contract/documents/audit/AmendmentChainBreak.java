package com.opencrm.contract.documents.audit;

import java.util.UUID;

/**
 * First place a document chain is not intact. A gap reports the missing sequence number.
 * A hash or previous-link failure reports the sequence of the row that failed.
 */
public record AmendmentChainBreak(UUID documentId, long seqNo, Kind kind) {

    public enum Kind {
        GAP,
        BROKEN_PREV_HASH,
        CONTENT_HASH_MISMATCH
    }
}
