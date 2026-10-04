-- Server-observed communication only, separate from client-reported QUEUE_OVERFLOW gaps.
CREATE TABLE monitoring_presence (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES monitoring_attempts(attempt_id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ONLINE','UNKNOWN')),
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('HEARTBEAT','HEARTBEAT_TIMEOUT','ACCESS_REVOKED','SERVER_RESTART')),
    revision BIGINT NOT NULL CHECK (revision > 0),
    collector_session_id VARCHAR(128) NOT NULL,
    socket_session_id VARCHAR(128) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    timeout_detected_at TIMESTAMPTZ
);
CREATE TABLE monitoring_interruptions (
    gap_id VARCHAR(128) PRIMARY KEY,
    attempt_id VARCHAR(128) NOT NULL REFERENCES monitoring_attempts(attempt_id),
    collector_session_id VARCHAR(128) NOT NULL,
    socket_session_id VARCHAR(128) NOT NULL,
    presence_revision BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL CHECK (reason = 'HEARTBEAT_TIMEOUT'),
    last_seen_at TIMESTAMPTZ NOT NULL,
    timeout_detected_at TIMESTAMPTZ NOT NULL,
    recovered_at TIMESTAMPTZ,
    CONSTRAINT monitoring_interruption_transition UNIQUE(attempt_id,presence_revision)
);
CREATE UNIQUE INDEX monitoring_one_open_interruption ON monitoring_interruptions(attempt_id) WHERE recovered_at IS NULL;
CREATE INDEX monitoring_interruption_history ON monitoring_interruptions(attempt_id,presence_revision);
