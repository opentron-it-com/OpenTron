ALTER TABLE connector_states
    ADD COLUMN IF NOT EXISTS pending_client_id VARCHAR(512);

ALTER TABLE connector_states
    ADD COLUMN IF NOT EXISTS pending_client_secret VARCHAR(512);

ALTER TABLE connector_states
    ADD COLUMN IF NOT EXISTS pending_token_url VARCHAR(512);
