-- V7: Exam Submits and Idempotency
CREATE TABLE IF NOT EXISTS exam_submit_requests (
    attempt_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    writer_epoch BIGINT NOT NULL,
    answer_revision BIGINT NOT NULL,
    answers_json TEXT NOT NULL,
    total_questions INT NOT NULL,
    correct_count INT NOT NULL,
    listening_correct INT NOT NULL,
    reading_correct INT NOT NULL,
    score INT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    decision_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_exam_submit_requests PRIMARY KEY (attempt_id, request_id),
    CONSTRAINT fk_exam_submit_requests_attempt FOREIGN KEY (attempt_id) REFERENCES monitoring_attempts (attempt_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exam_submit_requests_attempt ON exam_submit_requests (attempt_id);

ALTER TABLE monitoring_attempts ADD COLUMN IF NOT EXISTS total_questions INT;
ALTER TABLE monitoring_attempts ADD COLUMN IF NOT EXISTS correct_count INT;
ALTER TABLE monitoring_attempts ADD COLUMN IF NOT EXISTS listening_correct INT;
ALTER TABLE monitoring_attempts ADD COLUMN IF NOT EXISTS reading_correct INT;
