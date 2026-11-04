import { blocked } from './health.mjs';

/** A 4xx can come from the gateway; only a successful, shaped body establishes a read. */
export async function readAssetResponse(api, path, label, valid) {
  let body;
  try {
    body = await api.get(path);
  } catch (error) {
    const status = error?.status ?? error?.response?.status;
    throw blocked(`${label}: ${typeof status === 'number' ? `HTTP ${status}` : 'request failed'}; service behavior not established`);
  }
  if (!valid(body)) throw new Error(`${label}: unexpected successful response shape`);
  return body;
}
