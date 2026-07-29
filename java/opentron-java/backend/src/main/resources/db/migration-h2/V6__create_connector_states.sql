CREATE TABLE IF NOT EXISTS connector_states (
    connector_id        VARCHAR(64)   PRIMARY KEY,
    display_name        VARCHAR(128),
    connected           BOOLEAN       NOT NULL DEFAULT FALSE,
    credential_token    VARCHAR(2048),
    chunks_indexed      INTEGER       NOT NULL DEFAULT 0,
    last_sync_at        TIMESTAMP,
    last_sync_job_id    VARCHAR(64),
    sync_state          VARCHAR(32)   NOT NULL DEFAULT 'idle',
    sync_error          VARCHAR(1024),
    items_synced        INTEGER       NOT NULL DEFAULT 0,
    oldest_item_date    TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_connector_connected ON connector_states(connected);
CREATE INDEX IF NOT EXISTS idx_connector_sync_state ON connector_states(sync_state);
