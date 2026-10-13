ALTER TABLE monitoring_attempts ADD COLUMN listening_start_id VARCHAR(128);
ALTER TABLE monitoring_attempts ADD COLUMN listening_manifest_id CHAR(64);
ALTER TABLE monitoring_attempts ADD COLUMN listening_writer_epoch BIGINT;
ALTER TABLE monitoring_attempts ADD COLUMN listening_authorized_at TIMESTAMPTZ;
ALTER TABLE monitoring_attempts ADD COLUMN listening_last_seen_at TIMESTAMPTZ;
ALTER TABLE monitoring_attempts ADD COLUMN playback_started_at TIMESTAMPTZ;
ALTER TABLE monitoring_attempts ADD COLUMN playback_completed_at TIMESTAMPTZ;
ALTER TABLE monitoring_attempts ADD COLUMN listening_interrupted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_attempts ADD COLUMN listening_interrupted_at TIMESTAMPTZ;
ALTER TABLE monitoring_attempts ADD COLUMN listening_interruption_reason VARCHAR(32);
-- V9 may already have committed a start. An upgrade must never authorize replay of those active attempts.
UPDATE monitoring_attempts a SET listening_start_id = s.listening_start_id,
    listening_manifest_id = s.listening_manifest_id, listening_writer_epoch = a.writer_epoch,
    listening_authorized_at = s.started_at, listening_last_seen_at = s.started_at,
    listening_interrupted = a.state = 'ACTIVE',
    listening_interrupted_at = CASE WHEN a.state = 'ACTIVE' THEN clock_timestamp() ELSE NULL END,
    listening_interruption_reason = CASE WHEN a.state = 'ACTIVE' THEN 'SERVER_RESTART' ELSE NULL END,
    deadline_at = COALESCE(a.deadline_at, s.started_at + s.duration_seconds * INTERVAL '1 second')
FROM exam_sessions s WHERE a.session_id = s.session_id AND s.listening_start_id IS NOT NULL;
ALTER TABLE monitoring_attempts ADD CONSTRAINT listening_interruption_consistent CHECK (
    (NOT listening_interrupted AND listening_interrupted_at IS NULL AND listening_interruption_reason IS NULL)
    OR (listening_interrupted AND listening_interrupted_at IS NOT NULL AND listening_interruption_reason IS NOT NULL)
);
ALTER TABLE monitoring_attempts ADD CONSTRAINT listening_start_consistent CHECK (
    (listening_start_id IS NULL AND listening_manifest_id IS NULL AND listening_writer_epoch IS NULL
     AND listening_authorized_at IS NULL AND listening_last_seen_at IS NULL)
    OR (listening_start_id IS NOT NULL AND listening_manifest_id IS NOT NULL AND listening_writer_epoch IS NOT NULL AND listening_writer_epoch > 0
        AND listening_authorized_at IS NOT NULL AND listening_last_seen_at IS NOT NULL)
);
CREATE INDEX listening_active_heartbeat_idx ON monitoring_attempts(listening_last_seen_at)
    WHERE state = 'ACTIVE' AND listening_start_id IS NOT NULL AND NOT listening_interrupted AND playback_completed_at IS NULL;

CREATE TABLE listening_reorganizations (
    old_attempt_id VARCHAR(128) PRIMARY KEY REFERENCES monitoring_attempts(attempt_id),
    new_attempt_id VARCHAR(128) NOT NULL UNIQUE REFERENCES monitoring_attempts(attempt_id),
    new_session_id VARCHAR(128) NOT NULL UNIQUE REFERENCES exam_sessions(session_id),
    request_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (old_attempt_id <> new_attempt_id)
);
