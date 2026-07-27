-- Search that works before a word has been finished, and survives a typo.
--
-- Three problems with matching only the stemmed tsvector:
--
--   1. It matches whole lexemes, so nothing is found until a word is complete.
--   2. Prefix matching against it does not fix that, because the stem is often
--      shorter than what was typed: "deployment" is stored as `deploy`, which
--      does not start with `deploym`.
--   3. A misspelling is simply a different word, and a search box is where
--      misspellings arrive.
--
-- So two more ways to match are added beside it, and the query tries all three.

-- Unstemmed, for prefix matching. `simple` lowercases and splits on word
-- boundaries and does nothing else, so the lexemes are the words as written and
-- `deploym:*` reaches `deployment`. Kept alongside the stemmed vector rather than
-- replacing it: stemming is what lets "authenticate" find "authentication", which
-- no amount of prefix matching would.
ALTER TABLE documents
    ADD COLUMN search_simple TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(content, '')), 'B')
    ) STORED;

CREATE INDEX idx_documents_search_simple ON documents USING GIN (search_simple);

-- Trigram similarity compares the three-character sequences two strings share,
-- which survives a transposition, a doubled letter, or a missing one.
--
-- pg_trgm is a trusted extension from PostgreSQL 13 onwards, so the database
-- owner can install it without superuser rights -- which is what a self-hosted
-- deployment has.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Similarity against every title in a workspace would be a sequential scan. This
-- index makes it a lookup, and also serves the ILIKE '%fragment%' half of the
-- search, which no ordinary B-tree can.
CREATE INDEX idx_documents_title_trgm ON documents USING GIN (title gin_trgm_ops);
