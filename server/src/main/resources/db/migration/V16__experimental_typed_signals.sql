-- Append-only extension of V13. Experiment OFF disables routes, not default Flyway DDL.
ALTER TABLE monitoring_signal_events
    DROP CONSTRAINT monitoring_signal_events_event_type_check,
    DROP CONSTRAINT monitoring_signal_events_source_check,
    DROP CONSTRAINT monitoring_signal_events_metadata_quality_check;

ALTER TABLE monitoring_signal_events ADD CONSTRAINT monitoring_signal_events_typed_signal_check CHECK (
    (event_type = 'DISPLAY_COUNT_OBSERVED' AND source = 'JAVAFX_DISPLAY' AND metadata_quality = 'COMPLETE')
    OR (event_type IN ('USB_STORAGE_PRESENT_AT_START','USB_STORAGE_CONNECTED','USB_STORAGE_REMOVED')
        AND source = 'WINDOWS_USB_STORAGE' AND metadata_quality IN ('COMPLETE','PARTIAL'))
    OR (event_type = 'EXAM_FOCUS_CHANGED' AND source = 'JAVAFX_EXAM_FOCUS' AND metadata_quality = 'COMPLETE')
    OR (event_type = 'EXAM_WINDOW_STATE' AND source = 'JAVAFX_EXAM_WINDOW' AND metadata_quality = 'COMPLETE')
);
