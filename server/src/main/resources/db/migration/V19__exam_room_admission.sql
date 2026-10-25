-- Room invitations are separate from account passwords and existing exam timing.
CREATE TABLE exam_rooms (
    session_id VARCHAR(128) PRIMARY KEY REFERENCES exam_sessions(session_id),
    room_code VARCHAR(32) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE exam_room_members (
    session_id VARCHAR(128) NOT NULL REFERENCES exam_rooms(session_id),
    candidate_user_id BIGINT NOT NULL REFERENCES user_accounts(id),
    attempt_id VARCHAR(128) NOT NULL UNIQUE REFERENCES monitoring_attempts(attempt_id),
    pin_hash VARCHAR(100) NOT NULL,
    joined_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until TIMESTAMPTZ,
    PRIMARY KEY (session_id, candidate_user_id)
);

ALTER TABLE login_sessions ADD COLUMN room_attempt_id VARCHAR(128) REFERENCES monitoring_attempts(attempt_id);
