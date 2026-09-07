-- Kill switch: allows emergency shutdown of individual links or connections
ALTER TABLE smart_links ADD COLUMN killed_at TIMESTAMPTZ;
ALTER TABLE connections ADD COLUMN killed_at TIMESTAMPTZ;

-- Bot scans tracked separately to avoid inflating human metrics
ALTER TABLE handoff_sessions ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT false;
