import { DealStage, DealType, STAGE_COLORS } from '@frontend/shared-models';

/** Pipeline order, including closed stages. */
export const DEAL_STAGE_ORDER: readonly DealStage[] = [
  'PROSPECTING',
  'QUALIFICATION',
  'NEEDS_ANALYSIS',
  'PROPOSAL',
  'NEGOTIATION',
  'WON',
  'LOST',
];

export const DEAL_TYPES: readonly DealType[] = [
  'NEW_BUSINESS',
  'EXISTING_BUSINESS',
  'RENEWAL',
  'UPSELL',
  'CROSS_SELL',
];

export function dealStageLabel(stage: DealStage): string {
  switch (stage) {
    case 'PROSPECTING':
      return 'Prospecting';
    case 'QUALIFICATION':
      return 'Qualification';
    case 'NEEDS_ANALYSIS':
      return 'Needs Analysis';
    case 'PROPOSAL':
      return 'Proposal';
    case 'NEGOTIATION':
      return 'Negotiation';
    case 'WON':
      return 'Won';
    case 'LOST':
      return 'Lost';
    default: {
      const unhandled: never = stage;
      return unhandled;
    }
  }
}

export function dealStageColor(stage: DealStage): string {
  switch (stage) {
    case 'PROSPECTING':
    case 'QUALIFICATION':
    case 'NEEDS_ANALYSIS':
    case 'PROPOSAL':
    case 'NEGOTIATION':
    case 'WON':
    case 'LOST':
      return STAGE_COLORS[stage];
    default: {
      const unhandled: never = stage;
      return unhandled;
    }
  }
}

/** API advances one step. Negotiation closes through win or lose. */
export function canAdvanceDeal(stage: DealStage): boolean {
  switch (stage) {
    case 'PROSPECTING':
    case 'QUALIFICATION':
    case 'NEEDS_ANALYSIS':
    case 'PROPOSAL':
      return true;
    case 'NEGOTIATION':
    case 'WON':
    case 'LOST':
      return false;
    default: {
      const unhandled: never = stage;
      return unhandled;
    }
  }
}

export function canCloseDeal(stage: DealStage): boolean {
  switch (stage) {
    case 'PROSPECTING':
    case 'QUALIFICATION':
    case 'NEEDS_ANALYSIS':
    case 'PROPOSAL':
    case 'NEGOTIATION':
      return true;
    case 'WON':
    case 'LOST':
      return false;
    default: {
      const unhandled: never = stage;
      return unhandled;
    }
  }
}

/** Open pipeline used for header totals. Won and lost stay on the board but out of the sum. */
export function isOpenDealStage(stage: DealStage): boolean {
  switch (stage) {
    case 'PROSPECTING':
    case 'QUALIFICATION':
    case 'NEEDS_ANALYSIS':
    case 'PROPOSAL':
    case 'NEGOTIATION':
      return true;
    case 'WON':
    case 'LOST':
      return false;
    default: {
      const unhandled: never = stage;
      return unhandled;
    }
  }
}

export function dealTypeLabel(type: DealType): string {
  switch (type) {
    case 'NEW_BUSINESS':
      return 'New business';
    case 'EXISTING_BUSINESS':
      return 'Existing business';
    case 'RENEWAL':
      return 'Renewal';
    case 'UPSELL':
      return 'Upsell';
    case 'CROSS_SELL':
      return 'Cross-sell';
    default: {
      const unhandled: never = type;
      return unhandled;
    }
  }
}
