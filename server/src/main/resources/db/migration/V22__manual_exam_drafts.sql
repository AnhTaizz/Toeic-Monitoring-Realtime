ALTER TABLE exams ADD COLUMN authoring_status VARCHAR(16) NOT NULL DEFAULT 'READY'
    CHECK (authoring_status IN ('PENDING','READY'));
CREATE TABLE exam_manual_drafts (
    exam_id VARCHAR(128) PRIMARY KEY REFERENCES exams(exam_id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    completed_questions INT NOT NULL CHECK (completed_questions BETWEEN 0 AND 200),
    document TEXT NOT NULL
);

-- Enforce draft isolation for every session creation path, including future APIs.
CREATE FUNCTION require_ready_exam() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM exams WHERE exam_id=NEW.exam_id AND authoring_status='READY' FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Exam is pending or missing' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER exam_sessions_require_ready BEFORE INSERT OR UPDATE OF exam_id ON exam_sessions
    FOR EACH ROW EXECUTE FUNCTION require_ready_exam();
