CREATE TABLE event_type_mappings (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    raw_event_type VARCHAR(100) NOT NULL,
    canonical_event_type VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_event_type_mappings_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uk_event_type_mappings_org_raw_event_type
        UNIQUE (organization_id, raw_event_type)
);
