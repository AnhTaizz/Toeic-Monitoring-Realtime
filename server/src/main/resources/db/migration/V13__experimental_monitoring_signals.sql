-- Additive experiment only. Runtime endpoints are disabled unless explicitly enabled in TEST.
CREATE TABLE monitoring_signal_counters (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES monitoring_attempts(attempt_id),
    last_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_sequence BETWEEN 0 AND 9007199254740991)
);
CREATE TABLE monitoring_signal_events (
    attempt_id VARCHAR(128) NOT NULL REFERENCES monitoring_attempts(attempt_id),
    event_id VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    observer_session_id VARCHAR(128) NOT NULL,
    observation_sequence BIGINT NOT NULL CHECK (observation_sequence BETWEEN 1 AND 9007199254740991),
    event_type VARCHAR(64) NOT NULL CHECK (event_type = 'DISPLAY_COUNT_OBSERVED'),
    source VARCHAR(32) NOT NULL CHECK (source = 'JAVAFX_DISPLAY'),
    metadata_quality VARCHAR(16) NOT NULL CHECK (metadata_quality = 'COMPLETE'),
    observed_at TIMESTAMPTZ NOT NULL,
    observation_context VARCHAR(16) NOT NULL CHECK (observation_context IN ('CONNECTED','OFFLINE','UNSPECIFIED')),
    observation_connection_id VARCHAR(128),
    details JSONB NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    timeline_sequence BIGINT NOT NULL CHECK (timeline_sequence BETWEEN 1 AND 9007199254740991),
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    delivery_status VARCHAR(24) NOT NULL CHECK (delivery_status IN ('LIVE','BUFFERED_OFFLINE','PREVIOUS_CONNECTION','UNSPECIFIED')),
    PRIMARY KEY (attempt_id,event_id),
    UNIQUE (attempt_id,timeline_sequence),
    CHECK ((observation_context = 'CONNECTED') = (observation_connection_id IS NOT NULL))
);
