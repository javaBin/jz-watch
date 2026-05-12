CREATE TABLE keywords (
    id SERIAL PRIMARY KEY,
    name_en VARCHAR(100) NOT NULL,
    name_no VARCHAR(100) NOT NULL,
    UNIQUE (name_en, name_no)
);

-- Join table for videos and AI keywords
CREATE TABLE video_ai_keywords (
    video_id VARCHAR(100) NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    keyword_id INT NOT NULL REFERENCES keywords (id) ON DELETE CASCADE,
    PRIMARY KEY (video_id, keyword_id)
);

-- Index for searching keywords directly
CREATE INDEX idx_keywords_en ON keywords (name_en);

CREATE INDEX idx_keywords_no ON keywords (name_no);

-- Add localized ai_keywords columns to videos for faster search vector rebuilds and display
ALTER TABLE videos ADD COLUMN ai_keywords_en VARCHAR(200) [];

ALTER TABLE videos ADD COLUMN ai_keywords_no VARCHAR(200) [];

-- We keep the existing ai_keywords column on videos for now to avoid breaking everything,
-- but we will eventually migrate to using the join table for search vectors.