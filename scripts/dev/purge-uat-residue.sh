#!/usr/bin/env bash
# Soft-delete UAT test records that the API refuses to delete, in a LOCAL dev database only.
#
# The UAT harness (frontend/e2e) deletes what it creates through the API. Two kinds of record
# cannot be deleted that way, by design:
#   - ACCEPTED quotes (QUOTE_ACCEPTED_NO_DELETE): an accepted quote is a commercial record;
#   - contracts created from them when the outbox relay is on (no delete API).
# Every UAT record name starts with "UAT-" (frontend/e2e/uat/fixtures.mjs), so this script marks
# exactly those rows is_deleted = true, the same soft delete the services use. Nothing else changes.
#
# Usage:
#   scripts/dev/purge-uat-residue.sh            # dry run: count what would be purged
#   scripts/dev/purge-uat-residue.sh --apply    # purge
#
# Reaches Postgres through the compose container (PG_CONTAINER, default opencrm-postgres), or,
# when PG_CONTAINER is empty, through psql on DB_HOST (must be localhost or 127.0.0.1).
set -euo pipefail

apply=false
case "${1:-}" in
  --apply) apply=true ;;
  ""|--dry-run) ;;
  *) echo "usage: $0 [--dry-run|--apply]" >&2; exit 2 ;;
esac

PG_CONTAINER="${PG_CONTAINER-opencrm-postgres}"
DB_NAME="${DB_NAME:-opencrm}"
DB_USERNAME="${DB_USERNAME:-postgres}"

if [[ -n "$PG_CONTAINER" ]]; then
  psql_cmd=(docker exec -i "$PG_CONTAINER" psql -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 -At)
else
  host="${DB_HOST:-localhost}"
  if [[ "$host" != "localhost" && "$host" != "127.0.0.1" ]]; then
    echo "refusing: DB_HOST=$host is not a local database" >&2
    exit 1
  fi
  psql_cmd=(psql -h "$host" -p "${DB_PORT:-5432}" -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 -At)
fi

# Contracts first: they are found through their source quote (its uuid, as the QuoteAccepted
# consumer stores it, or its number).
contracts_where="not is_deleted and quote_id in (select uuid from quote.quotes where name like 'UAT-%' union select quote_number from quote.quotes where name like 'UAT-%')"
quotes_where="not is_deleted and name like 'UAT-%' and status = 'ACCEPTED'"

count_sql="select 'contracts', count(*) from contract.subscriptions where $contracts_where
union all select 'accepted quotes', count(*) from quote.quotes where $quotes_where;"

if ! $apply; then
  echo "dry run (use --apply to purge):"
  "${psql_cmd[@]}" -c "$count_sql" | sed 's/|/: /'
  exit 0
fi

"${psql_cmd[@]}" <<SQL | sed 's/^UPDATE /soft-deleted /'
begin;
update contract.subscriptions set is_deleted = true, updated_at = now() where $contracts_where;
update quote.quotes set is_deleted = true, updated_at = now(), updated_by = 'purge-uat-residue' where $quotes_where;
commit;
SQL
