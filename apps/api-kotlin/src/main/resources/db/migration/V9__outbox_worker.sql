-- Add status column and retry scheduling to the existing outbox_events table
ALTER TABLE outbox_events
    ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD_LETTER'));

ALTER TABLE outbox_events
    ADD COLUMN next_retry_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Partial index: the worker only ever scans PENDING rows
CREATE INDEX outbox_events_pending_idx
    ON outbox_events (next_retry_at)
    WHERE status = 'PENDING';

-- Backfill: already-published rows should be SUCCEEDED
UPDATE outbox_events SET status = 'SUCCEEDED' WHERE published_at IS NOT NULL;
