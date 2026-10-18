-- psql -W prompts privately. Use TEST DB only, supplied via environment outside Git.
-- psql -X -W -v ON_ERROR_STOP=1 -v attempt_a=... -v attempt_b=... -f verify-history.sql
-- Output aliases/digests only; keep full raw privately. No mutation / schema cleanup here.
BEGIN TRANSACTION READ ONLY;
WITH picked AS (
  SELECT 'CANDIDATE_A' AS candidate, :'attempt_a'::text AS attempt_id
  UNION ALL SELECT 'CANDIDATE_B', :'attempt_b'::text
)
SELECT p.candidate, a.state, a.writer_epoch, a.saved_revision, a.deadline_at, a.submitted_at,
       md5(a.answers_json::jsonb::text) AS committed_answers_digest,
       a.listening_interrupted, a.listening_interrupted_at,
       a.listening_interruption_reason,
       (SELECT count(*) FROM monitoring_events e WHERE e.attempt_id=a.attempt_id) AS events,
       (SELECT count(*) FROM monitoring_gaps g WHERE g.attempt_id=a.attempt_id) AS gaps,
       (SELECT count(*) FROM monitoring_events e WHERE e.attempt_id=a.attempt_id)
         - (SELECT count(DISTINCT event_id) FROM monitoring_events e WHERE e.attempt_id=a.attempt_id) AS duplicate_event_ids,
       (SELECT count(*) FROM monitoring_gaps g WHERE g.attempt_id=a.attempt_id)
         - (SELECT count(DISTINCT gap_id) FROM monitoring_gaps g WHERE g.attempt_id=a.attempt_id) AS duplicate_gap_ids
FROM picked p JOIN monitoring_attempts a USING (attempt_id) ORDER BY p.candidate;
ROLLBACK;
-- SQL MD5 is a local equality digest only; it is not the checkpoint SHA256 format.
-- Unchanged count is not sufficient: correlate exact eventId/gapId with HTTP checkpoints/ACKs.
