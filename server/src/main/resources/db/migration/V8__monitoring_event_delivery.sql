-- Additive only. Existing events have unknown delivery provenance, not invented lateness.
ALTER TABLE monitoring_events
    ADD COLUMN observation_context VARCHAR(24) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN observation_connection_id VARCHAR(128),
    ADD COLUMN delivery_status VARCHAR(32) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD CONSTRAINT monitoring_event_origin CHECK (
        (observation_context = 'CONNECTED' AND observation_connection_id IS NOT NULL) OR
        (observation_context IN ('OFFLINE','UNSPECIFIED') AND observation_connection_id IS NULL)),
    ADD CONSTRAINT monitoring_event_delivery CHECK (
        delivery_status IN ('LIVE','BUFFERED_OFFLINE','PREVIOUS_CONNECTION','UNSPECIFIED'));
