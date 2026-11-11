-- Additive provenance for DRG-427. Existing selections remain seller-authored.
CREATE TABLE configuration_default_selections (
    session_id BIGINT NOT NULL,
    option_key VARCHAR(255) NOT NULL,
    PRIMARY KEY (session_id, option_key),
    FOREIGN KEY (session_id) REFERENCES configuration_sessions(id) ON DELETE CASCADE
);
