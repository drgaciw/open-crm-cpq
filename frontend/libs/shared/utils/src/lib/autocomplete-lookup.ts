/**
 * Helpers for Material autocomplete fields that link a record by id (contact, organization).
 *
 * The control holds a picked option object, but a plain label string when the form is patched
 * from an existing record or when the user types without picking (H-CRM-022/023/033/034).
 */
export type LookupResolution = { ok: true; id: number | null } | { ok: false };

/** The record currently linked through the field, with the label it was shown under. */
export interface LookupSelection {
  id: number;
  label: string | null;
}

const normalize = (text: string | null | undefined): string => (text ?? '').trim().toLowerCase();

/**
 * Resolve an autocomplete control value to a record id: a picked option, the unchanged linked
 * record, typed text that exactly names one option, or blank (no link). Anything else is
 * unresolved, so the caller can block the save instead of silently dropping the link.
 */
export function resolveLookupId<T extends { id: number }>(
  value: unknown,
  selected: LookupSelection | null,
  options: readonly T[],
  labelOf: (option: T) => string,
): LookupResolution {
  if (value !== null && value !== undefined && typeof value === 'object') {
    return { ok: true, id: (value as T).id };
  }
  const text = normalize(value === null || value === undefined ? '' : String(value));
  if (!text) return { ok: true, id: null };
  if (selected && normalize(selected.label) === text) return { ok: true, id: selected.id };
  const matches = options.filter((option) => normalize(labelOf(option)) === text);
  return matches.length === 1 ? { ok: true, id: matches[0].id } : { ok: false };
}

/** A displayWith function that shows option labels and passes plain strings through. */
export function displayLookup<T>(labelOf: (option: T) => string): (value: T | string | null | undefined) => string {
  return (value) => {
    if (value === null || value === undefined) return '';
    return typeof value === 'string' ? value : labelOf(value);
  };
}
