-- Revert v2 keyword creation approach
-- We keep ai_keywords_en and ai_keywords_no on the videos table, 
-- but remove the separate keywords table and the join table.
-- We also remove the merged ai_keywords column.

DROP TABLE IF EXISTS video_ai_keywords;
DROP TABLE IF EXISTS keywords;

ALTER TABLE videos DROP COLUMN IF EXISTS ai_keywords;
