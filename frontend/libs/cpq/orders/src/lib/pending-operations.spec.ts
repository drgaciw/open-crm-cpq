import { PendingOperations } from './pending-operations';

describe('pending operation journal', () => {
  beforeEach(() => sessionStorage.clear());
  it('retains only operation metadata across new instances, never command bodies', () => {
    const journal = new PendingOperations();
    journal.begin('order', 'o', {
      operationKey: 'key',
      action: 'evidence',
      expectedVersion: 4,
    });
    expect(new PendingOperations().read('order', 'o')).toEqual([
      { operationKey: 'key', action: 'evidence', expectedVersion: 4 },
    ]);
    expect(sessionStorage.getItem('oms:pending:order:o')).not.toContain(
      'reason',
    );
    journal.complete('order', 'o', 'key');
    expect(journal.read('order', 'o')).toEqual([]);
  });
  it('fails closed on unavailable or corrupted storage', () => {
    sessionStorage.setItem('oms:pending:order:o', 'bad-json');
    expect(() => new PendingOperations().read('order', 'o')).toThrow();
  });
});
