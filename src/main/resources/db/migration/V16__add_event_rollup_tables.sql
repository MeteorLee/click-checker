CREATE TABLE event_hourly_rollups (
    organization_id BIGINT NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    path VARCHAR(512) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_count BIGINT NOT NULL,
    identified_event_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_event_hourly_rollups
        PRIMARY KEY (organization_id, bucket_start, path, event_type),
    CONSTRAINT fk_event_hourly_rollups_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE event_rollup_watermarks (
    organization_id BIGINT PRIMARY KEY,
    processed_created_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_rollup_watermarks_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_events_organization_id_created_at
    ON events (organization_id, created_at);
