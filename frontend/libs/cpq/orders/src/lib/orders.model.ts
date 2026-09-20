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
  productId: string;
  description: string;
  fulfillmentType: string;
  chargeType: string;
  quantity: number;
  uom: string;
  unitPrice: string;
  discount: string;
  tax: string;
  total: string;
  billingFrequency: string;
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
  subtotal: string;
  discount: string;
  tax: string;
  total: string;
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
  | 'FINANCE_ACK';
export const ORDER_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'IN_PROGRESS',
  'COMPLETED',
  'PARTIALLY_COMPLETED',
  'CANCELLED',
];
