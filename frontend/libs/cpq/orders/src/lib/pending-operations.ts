import { Injectable } from '@angular/core';

export interface PendingOperation {
  operationKey: string;
  action: string;
  expectedVersion: number;
}
export type OperationEntity = 'order' | 'exception';

/** Session journal contains opaque operation metadata only, never bodies, tokens or commercial facts. */
@Injectable({ providedIn: 'root' })
export class PendingOperations {
  read(kind: OperationEntity, id: string): PendingOperation[] {
    const raw = sessionStorage.getItem(this.key(kind, id));
    if (!raw) return [];
    const entries: unknown = JSON.parse(raw);
    if (
      !Array.isArray(entries) ||
      entries.some(
        (entry) =>
          !entry ||
          typeof entry.operationKey !== 'string' ||
          typeof entry.action !== 'string' ||
          !Number.isSafeInteger(entry.expectedVersion),
      )
    )
      throw new Error(
        'Pending operation journal is unavailable. Ask an operator to reconcile before continuing.',
      );
    return entries;
  }
  begin(kind: OperationEntity, id: string, entry: PendingOperation) {
    const entries = this.read(kind, id);
    sessionStorage.setItem(
      this.key(kind, id),
      JSON.stringify([...entries, entry]),
    );
  }
  complete(kind: OperationEntity, id: string, key: string) {
    const entries = this.read(kind, id).filter(
      (entry) => entry.operationKey !== key,
    );
    if (entries.length)
      sessionStorage.setItem(this.key(kind, id), JSON.stringify(entries));
    else sessionStorage.removeItem(this.key(kind, id));
  }
  private key(kind: OperationEntity, id: string) {
    return `oms:pending:${kind}:${id}`;
  }
}

export function definitiveRejection(error: unknown): boolean {
  return [400, 401, 403, 404, 409, 422].includes(
    (error as { status?: number })?.status ?? 0,
  );
}
