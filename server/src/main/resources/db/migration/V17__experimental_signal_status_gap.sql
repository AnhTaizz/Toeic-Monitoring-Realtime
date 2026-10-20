-- USER_AUTHORIZED_EXPERIMENT. Flag OFF gates runtime only; Flyway still applies DDL.
-- Existing event/counter rows, checksums and process event/gap constraints stay unchanged.
ALTER TABLE monitoring_signal_events DROP CONSTRAINT monitoring_signal_events_typed_signal_check;
ALTER TABLE monitoring_signal_events ADD CONSTRAINT monitoring_signal_events_typed_signal_check CHECK (
    (event_type = 'DISPLAY_COUNT_OBSERVED' AND source = 'JAVAFX_DISPLAY' AND metadata_quality = 'COMPLETE')
    OR (event_type IN ('USB_STORAGE_PRESENT_AT_START','USB_STORAGE_CONNECTED','USB_STORAGE_REMOVED')
        AND source = 'WINDOWS_USB_STORAGE' AND metadata_quality IN ('COMPLETE','PARTIAL'))
    OR (event_type = 'EXAM_FOCUS_CHANGED' AND source = 'JAVAFX_EXAM_FOCUS' AND metadata_quality = 'COMPLETE')
    OR (event_type = 'EXAM_WINDOW_STATE' AND source = 'JAVAFX_EXAM_WINDOW' AND metadata_quality = 'COMPLETE')
    OR (event_type = 'OBSERVER_STATUS'
        AND source IN ('WINDOWS_USB_STORAGE','JAVAFX_EXAM_FOCUS','JAVAFX_EXAM_WINDOW','JAVAFX_DISPLAY')
        AND metadata_quality IN ('COMPLETE','UNREADABLE','UNSUPPORTED'))
    OR (event_type = 'SIGNAL_GAP' AND source = 'CLIENT_SIGNAL_DELIVERY' AND metadata_quality = 'COMPLETE')
);
