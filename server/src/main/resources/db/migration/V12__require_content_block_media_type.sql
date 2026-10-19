-- V11 CHECK accepted UNKNOWN when an IMAGE/AUDIO block supplied NULL media_type.
-- PostgreSQL's default MATCH SIMPLE foreign key also skips a composite key containing
-- NULL, so that row could reference a missing or differently typed media asset.
-- Keep V11 unchanged; require the missing discriminator under its existing contract.
-- Invalid previously stored rows make this migration fail transactionally.  Do not
-- guess a type, delete content, or mark those rows verified during an upgrade.
ALTER TABLE exam_content_blocks
    ADD CONSTRAINT exam_content_blocks_media_type_required
        CHECK (block_type = 'TEXT' OR media_type IS NOT NULL);
