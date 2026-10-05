CREATE TABLE IF NOT EXISTS exam_autosave_requests (
    attempt_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    writer_epoch BIGINT NOT NULL,
    answer_revision BIGINT NOT NULL,
    answers_json TEXT NOT NULL,
    decision_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_exam_autosave_requests PRIMARY KEY (attempt_id, request_id),
    CONSTRAINT fk_exam_autosave_requests_attempt FOREIGN KEY (attempt_id) REFERENCES monitoring_attempts (attempt_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exam_autosave_requests_attempt ON exam_autosave_requests (attempt_id);
