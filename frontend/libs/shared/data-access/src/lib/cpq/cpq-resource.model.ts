import { Identifiable } from '@frontend/shared-models';

/** Minimal CPQ resource handle until published DTOs land in shared-models. */
export interface CpqResource extends Identifiable {
  id: string;
}

export type CpqResourceCreate = Record<string, unknown>;
export type CpqResourceUpdate = Record<string, unknown>;
