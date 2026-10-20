-- EX-03 only: preserve legacy defaults; session/readiness changes belong to EX-07.
-- V13 is reserved by open MON-07 PR74; coordinate migration order before merging.
ALTER TABLE exams ADD COLUMN content_version SMALLINT NOT NULL DEFAULT 1
    CHECK (content_version IN (1, 2));
ALTER TABLE exam_sources ADD COLUMN option_content_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED'
    CHECK (option_content_status IN ('UNVERIFIED', 'VERIFIED'));
ALTER TABLE exam_content_blocks
    ADD COLUMN document_id VARCHAR(128),
    ADD COLUMN document_order INT,
    ADD COLUMN document_title VARCHAR(255),
    ADD CONSTRAINT exam_content_blocks_document_pair CHECK (
        (document_id IS NULL AND document_order IS NULL AND document_title IS NULL)
        OR (document_id IS NOT NULL AND btrim(document_id) <> ''
            AND document_order IS NOT NULL AND document_order > 0 AND group_id IS NOT NULL)
    );
-- Cross-row document ID/order/title consistency is validated atomically by the importer.
-- Existing unique owner/block order indexes continue to apply across all documents.
