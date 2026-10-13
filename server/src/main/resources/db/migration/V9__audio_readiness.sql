-- Readiness belongs to the exact resource manifest and writer generation.
CREATE TABLE attempt_audio_readiness (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES monitoring_attempts(attempt_id) ON DELETE CASCADE,
    writer_epoch BIGINT NOT NULL CHECK (writer_epoch > 0),
    manifest_id CHAR(64) NOT NULL CHECK (manifest_id ~ '^[a-f0-9]{64}$'),
    ready_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE exam_sessions ADD COLUMN listening_start_id VARCHAR(128);
ALTER TABLE exam_sessions ADD COLUMN listening_manifest_id CHAR(64);
ALTER TABLE exam_sessions ADD CONSTRAINT exam_sessions_listening_start_pair
    CHECK ((listening_start_id IS NULL) = (listening_manifest_id IS NULL));
CREATE UNIQUE INDEX exam_sessions_listening_start_id_idx ON exam_sessions(listening_start_id)
    WHERE listening_start_id IS NOT NULL;
