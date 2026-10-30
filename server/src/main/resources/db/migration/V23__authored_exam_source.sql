-- Manual authoring has its own factual provenance, distinct from licensed external imports.
ALTER TABLE exam_sources DROP CONSTRAINT exam_sources_permission_status_check;
ALTER TABLE exam_sources ADD CONSTRAINT exam_sources_permission_status_check
    CHECK (permission_status IN ('UNKNOWN','USER_CONFIRMED','DOCUMENTED','LICENSED','AUTHORED'));
