ALTER TABLE exam_rooms
    ADD COLUMN admission_mode VARCHAR(24) NOT NULL DEFAULT 'LEGACY_PIN'
        CHECK (admission_mode IN ('LEGACY_PIN','ACCOUNT_PIN')),
    ADD COLUMN shared_pin_hash VARCHAR(100),
    ADD COLUMN shared_pin_lookup CHAR(64),
    ADD CONSTRAINT exam_rooms_shared_pin_shape CHECK (
        (admission_mode='LEGACY_PIN' AND shared_pin_hash IS NULL AND shared_pin_lookup IS NULL)
        OR (admission_mode='ACCOUNT_PIN' AND shared_pin_hash IS NOT NULL
            AND shared_pin_lookup IS NOT NULL
            AND shared_pin_lookup ~ '^[a-f0-9]{64}$')
    );
CREATE UNIQUE INDEX exam_rooms_shared_pin_unique ON exam_rooms(shared_pin_lookup)
    WHERE admission_mode='ACCOUNT_PIN';
ALTER TABLE exam_room_members ALTER COLUMN pin_hash DROP NOT NULL;

CREATE TABLE account_pin_join_limits (
    user_id BIGINT PRIMARY KEY REFERENCES user_accounts(id),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    locked_until TIMESTAMPTZ
);
