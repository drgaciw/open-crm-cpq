-- J5 gate A evidence only. Run with psql -X -v ON_ERROR_STOP=1 -f this-file.
-- PG* environment or a service file supplies the connection; never embed a password.
-- Missing table/column/permissions are errors, never evidence of zero pending rows.
BEGIN TRANSACTION READ ONLY;
SELECT json_build_object(
    'gate', 'J5-A',
    'database', current_database(),
    'observed_at', clock_timestamp(),
    'local_pending', COUNT(*),
    'empty_local_pending', COUNT(*) = 0,
    'cutover_authorized', FALSE,
    'remaining_gates', json_build_array('environment soak', 'named rollback owner', 'rollback drill', 'PM authorization')
)
FROM quote.approval_requests
WHERE status = 'PENDING' AND is_deleted = FALSE;
ROLLBACK;
