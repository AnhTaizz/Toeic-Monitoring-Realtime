# Explicit DEV/TEST fixture only. Never called by login or server startup.
param(
    [ValidateSet('Create', 'Cleanup')][string]$Action = 'Create',
    [ValidatePattern('^(public|c3_demo_test_[a-f0-9]{32})$')][string]$Schema = 'public'
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $sql = if ($Action -eq 'Create') {
@'
BEGIN;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM user_accounts WHERE username='candidate1' AND role='CANDIDATE' AND enabled)
       OR NOT EXISTS (SELECT 1 FROM user_accounts WHERE username='proctor1' AND role='PROCTOR' AND enabled) THEN
        RAISE EXCEPTION 'DEV fixture requires enabled candidate1/proctor1; start server first';
    END IF;
    IF EXISTS (SELECT 1 FROM monitoring_attempts WHERE attempt_id='DEMO-C3-A') THEN
        RAISE EXCEPTION 'DEV attempt already exists; use Cleanup explicitly before Create';
    END IF;
END $$;
INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state)
SELECT 'DEMO-C3-A',id,'ACTIVE' FROM user_accounts WHERE username='candidate1';
INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id)
SELECT 'DEMO-C3-A',id FROM user_accounts WHERE username='proctor1';
COMMIT;
'@
    } else {
@'
BEGIN;
DO $$ BEGIN
    PERFORM 1 FROM monitoring_attempts WHERE attempt_id='DEMO-C3-A' FOR UPDATE;
    IF EXISTS (SELECT 1 FROM monitoring_attempts a JOIN user_accounts u ON u.id=a.candidate_user_id
               WHERE a.attempt_id='DEMO-C3-A' AND (u.username<>'candidate1' OR u.role<>'CANDIDATE'))
       OR EXISTS (SELECT 1 FROM monitoring_proctor_assignments a JOIN user_accounts u ON u.id=a.proctor_user_id
                  WHERE a.attempt_id='DEMO-C3-A' AND (u.username<>'proctor1' OR u.role<>'PROCTOR')) THEN
        RAISE EXCEPTION 'Refusing cleanup: DEMO attempt owner/assignment differs';
    END IF;
END $$;
DELETE FROM monitoring_gaps WHERE attempt_id='DEMO-C3-A';
DELETE FROM monitoring_events WHERE attempt_id='DEMO-C3-A';
DELETE FROM monitoring_proctor_assignments WHERE attempt_id='DEMO-C3-A';
DELETE FROM monitoring_attempts WHERE attempt_id='DEMO-C3-A';
COMMIT;
'@
    }
    # Schema is constrained above; credentials stay inside the DB container.
    ("SET search_path TO $Schema;`n" + $sql) | docker compose exec -T db sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    if ($LASTEXITCODE -ne 0) { throw 'DEV/TEST fixture failed; database reset is not required.' }
    Write-Output "DEV/TEST DEMO-C3-A $Action complete. No user accounts or other attempts changed."
} finally { Pop-Location }
