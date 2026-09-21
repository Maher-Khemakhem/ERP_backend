-- Ensure event_publication table exists with appropriate column types
-- and alter existing columns to TEXT/VARCHAR(512) so serialized event JSON
-- payloads are not constrained by VARCHAR(255).

CREATE TABLE IF NOT EXISTS event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
    completion_attempts INT NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

ALTER TABLE event_publication
    ALTER COLUMN serialized_event TYPE TEXT,
    ALTER COLUMN listener_id TYPE VARCHAR(512),
    ALTER COLUMN event_type TYPE VARCHAR(512);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
