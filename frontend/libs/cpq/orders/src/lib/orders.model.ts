export interface OrderPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface SourceReference {
  snapshotId: string;
  quoteId: string;
  acceptedRevision: number;
}
export interface SnapshotLine {
  sourceLineId: string;
  productId: string | null;
  description: string | null;
  fulfillmentType: string | null;
  chargeType: string | null;
  quantity: number;
  uom: string | null;
  unitPrice: string | null;
  discount: string | null;
  tax: string | null;
  total: string | null;
  billingFrequency: string | null;
  startDate: string | null;
  endDate: string | null;
}
export interface OrderSnapshot extends SourceReference {
  schemaVersion: number;
  quoteNumber: string;
  scopeId: string;
  customerId: string;
  acceptedAt: string;
  currency: string;
  subtotal: string | null;
  discount: string | null;
  tax: string | null;
  total: string | null;
  address: string | null;
  approvalReference: string | null;
  taxEvidence: string | null;
  configurationReference: string | null;
  lines: SnapshotLine[];
}
export interface OrderLine {
  id: string;
  description: string;
  ordered: number;
  cancelled: number;
  shipped: number;
  delivered: number;
  remaining: number;
  allocated: number;
  status: string;
}
export interface OrderView {
  id: string;
  orderNumber: string;
  quoteId: string;
  quoteNumber: string;
  customerId: string;
  scopeId: string;
  status: string;
  version: number;
  snapshot: OrderSnapshot;
  lines: OrderLine[];
  holds: string[];
  financeStatus: string;
}
export interface OrderTask {
  id: string;
  lineId: string;
  kind: string;
  templateVersion: number;
  status: string;
  dispatchedAt: string | null;
}
export interface OrderTimeline {
  actor: string;
  at: string;
  action: string;
  reason: string;
  version: number;
  evidenceReference: string | null;
}
export interface OrderException {
  id: string;
  orderId: string;
  taskId: string | null;
  category: string;
  severity: string;
  owner: string | null;
  reason: string;
  nextAction: string;
  status: 'OPEN' | 'RESOLVED';
  firstOccurredAt: string;
  lastOccurredAt: string;
  version: number;
}
export interface Command {
  expectedVersion: number;
  reason: string;
  operationKey: string;
}
export type EvidenceKind =
  | 'ALLOCATION'
  | 'SHIPMENT'
  | 'DELIVERY'
  | 'SERVICE'
  | 'PROVISION'
  | 'RELEASE'
  | 'FINANCE_ACK';
export interface OperationStatus {
  operationKey: string;
  orderId: string;
  status: 'SUCCEEDED' | 'ABANDONED' | 'NOT_FOUND' | 'UNKNOWN' | 'PENDING';
  orderVersion: number;
}
export interface FulfillmentEvidence {
  id: string;
  lineId: string;
  kind: string;
  quantity: number;
  externalReference: string;
  allocationReference: string | null;
  shipmentReference: string | null;
  occurredAt: string;
  expiresAt: string | null;
  actor: string;
  reason: string;
  status: string;
  detail: string | null;
}
export interface Allocation {
  id: string;
  lineId: string;
  reference: string;
  quantity: number;
  consumed: number;
  released: number;
  available: number;
  expiresAt: string | null;
}
export interface FinanceRequest {
  requestKey: string;
  lineId: string;
  quantity: number;
  acknowledgedQuantity: number;
  status: string;
  payload: string;
}
export type FinanceOutcome = 'ACKNOWLEDGED' | 'REJECTED' | 'UNKNOWN';
export const ORDER_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'IN_PROGRESS',
  'COMPLETED',
  'PARTIALLY_COMPLETED',
  'CANCELLED',
];
