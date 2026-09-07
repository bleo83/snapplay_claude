-- Approval and activation tracking for immutability enforcement
ALTER TABLE commercial_contracts ADD COLUMN timezone TEXT NOT NULL DEFAULT 'America/Argentina/Buenos_Aires';
ALTER TABLE commercial_contracts ADD COLUMN close_day_of_month INT NOT NULL DEFAULT 1 CHECK (close_day_of_month BETWEEN 1 AND 28);
ALTER TABLE commercial_contracts ADD COLUMN activated_at TIMESTAMPTZ;

ALTER TABLE contract_versions ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE contract_versions ADD COLUMN approved_by UUID;

-- Prevent overlapping approved versions for the same contract
CREATE UNIQUE INDEX contract_versions_no_overlap_idx
    ON contract_versions (contract_id)
    WHERE status = 'APPROVED' AND effective_to IS NULL;
