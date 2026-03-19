CREATE INDEX idx_events_organization_id_occurred_at
    ON events (organization_id, occurred_at);
