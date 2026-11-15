import { displayLookup, resolveLookupId } from './autocomplete-lookup';

interface Org {
  id: number;
  name: string;
}

const ORGS: Org[] = [
  { id: 3, name: 'Acme Corp' },
  { id: 7, name: 'Globex' },
];
const name = (o: Org) => o.name;

describe('resolveLookupId', () => {
  it('uses a picked option', () => {
    expect(resolveLookupId(ORGS[1], null, ORGS, name)).toEqual({ ok: true, id: 7 });
  });

  it('treats blank text as no link', () => {
    expect(resolveLookupId('   ', null, ORGS, name)).toEqual({ ok: true, id: null });
    expect(resolveLookupId(null, null, ORGS, name)).toEqual({ ok: true, id: null });
  });

  it('keeps the selected record while its label is unchanged', () => {
    expect(resolveLookupId('globex ', { id: 7, label: 'Globex' }, [], name)).toEqual({ ok: true, id: 7 });
  });

  it('resolves typed text that exactly names one option (case-insensitive)', () => {
    expect(resolveLookupId('acme corp', null, ORGS, name)).toEqual({ ok: true, id: 3 });
  });

  it('does not resolve text that matches nothing or more than one option', () => {
    expect(resolveLookupId('Initech', null, ORGS, name)).toEqual({ ok: false });
    expect(resolveLookupId('Twin', null, [{ id: 1, name: 'Twin' }, { id: 2, name: 'twin' }], name)).toEqual({ ok: false });
  });

  it('does not keep a stale selection once the text changes', () => {
    expect(resolveLookupId('Something else', { id: 7, label: 'Globex' }, ORGS, name)).toEqual({ ok: false });
  });
});

describe('displayLookup', () => {
  const display = displayLookup(name);

  it('shows plain strings as they are (patched names)', () => {
    expect(display('Globex')).toBe('Globex');
  });

  it('labels options', () => {
    expect(display(ORGS[0])).toBe('Acme Corp');
  });

  it('shows nothing for empty values', () => {
    expect(display(null)).toBe('');
    expect(display(undefined)).toBe('');
  });
});
