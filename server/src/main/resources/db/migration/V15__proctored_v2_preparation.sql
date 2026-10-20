-- EX07 append-only; V13/V14 merge/deploy coordination remains a required gate.
ALTER TABLE exam_sessions ADD COLUMN contract_version VARCHAR(24) NOT NULL DEFAULT 'LEGACY_V1'
    CHECK (contract_version IN ('LEGACY_V1','PROCTORED_V2'));
ALTER TABLE monitoring_attempts ADD COLUMN preparation_epoch VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN committed_visual_manifest_id CHAR(64) CHECK (committed_visual_manifest_id ~ '^[a-f0-9]{64}$');
ALTER TABLE attempt_audio_readiness ADD COLUMN preparation_epoch VARCHAR(36);
CREATE TABLE attempt_visual_readiness (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES monitoring_attempts(attempt_id) ON DELETE CASCADE,
    writer_epoch BIGINT NOT NULL CHECK (writer_epoch > 0),
    preparation_epoch VARCHAR(36) NOT NULL,
    manifest_id CHAR(64) NOT NULL CHECK (manifest_id ~ '^[a-f0-9]{64}$'),
    attested_token_hash CHAR(64) NOT NULL REFERENCES login_sessions(token_hash),
    asset_digest CHAR(64) NOT NULL,
    ready_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    CHECK (last_seen_at >= ready_at AND lease_expires_at > last_seen_at)
);
CREATE TABLE visual_ready_requests (
    attempt_id VARCHAR(128) NOT NULL REFERENCES monitoring_attempts(attempt_id) ON DELETE CASCADE,
    request_id VARCHAR(128) NOT NULL,
    payload_digest CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY (attempt_id,request_id)
);
