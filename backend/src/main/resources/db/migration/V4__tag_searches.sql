CREATE TABLE tag_searches (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag        VARCHAR(200) NOT NULL,
    ip_address TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX tag_searches_tag_idx ON tag_searches (tag);
