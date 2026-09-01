-- SNA-23: Make contract_version_id nullable so it can be resolved lazily by the billing
-- pipeline. At QR-scan time we have the connection_id (which implies the contract) but
-- resolving the exact contract_version requires an extra join that adds latency to the
-- critical redirect path. The connection_id FK preserves referential integrity.
ALTER TABLE handoff_sessions
    ALTER COLUMN contract_version_id DROP NOT NULL;

-- Index for the expiry sweeper (future SNA)
CREATE INDEX handoff_sessions_expires_status_idx
    ON handoff_sessions (expires_at, status)
    WHERE status NOT IN ('CONVERTED', 'EXPIRED', 'FAILED');

-- Index for smart_link dashboard aggregation
CREATE INDEX handoff_sessions_smart_link_idx
    ON handoff_sessions (smart_link_id);
