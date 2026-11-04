/**
 * Amendment document models (CAM document spine).
 * Mirrors: com.opencrm.contract.documents.AmendmentView / CreateAmendment.
 *
 * HTTP API exists on CAM but is **not gateway-routed** yet (AmendmentController
 * Javadoc). Types keep the SPA ready for the released surface.
 */

/** Mirrors: com.opencrm.contract.domain.documents.AmendmentDocumentStatus (stringly until FE enum sync). */
export type AmendmentDocumentStatus = string;

/**
 * Mirrors: com.opencrm.contract.documents.AmendmentView
 * GET /api/v1/amendments/{amendmentId} (CAM-direct today)
 */
export interface AmendmentView {
  id: string;
  contractId: string;
  provisionalNumber?: string;
  amendmentSeq?: number;
  amendmentNumber?: string;
  status: AmendmentDocumentStatus;
  version: number;
  contentDigest?: string;
  approvalRef?: string;
  signatureEnvelopeRef?: string;
  tcvDelta?: number;
  arrDelta?: number;
  oneTimeTotal?: number;
  creditTotal?: number;
  revisions?: unknown[];
  lines?: unknown[];
}

/**
 * Mirrors: com.opencrm.contract.documents.CreateAmendment
 * POST /api/v1/contracts/{contractId}/amendments
 */
export interface CreateAmendment {
  lifecycleChangeId?: string;
  lifecycleChangeRevision: number;
  targetQuoteId?: string;
  inputsSnapshotRef?: string;
  signaturePolicy?: string;
  actionDate?: string;
  contractEffectiveDate?: string;
  backdatingReasonCode?: string;
  currency?: string;
  lines?: unknown[];
}
