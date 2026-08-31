-- Phase 3: making memories findable.
--
-- The searchable form of a memory is derived by PostgreSQL rather than maintained by the application.
-- A generated column cannot drift from the row it describes and no future writer can forget to update
-- it, which a trigger or an application-side field could both allow. The price is that the expression
-- must be immutable, so every call below names its text search configuration explicitly instead of
-- relying on default_text_search_config, which is only STABLE because a session can change it.
--
-- Note for later: ADD COLUMN ... GENERATED rewrites the table. Free at current size, but once this
-- table is large the same change wants a nullable column, a backfill in batches, and then the
-- constraint.

-- Fields are weighted rather than concatenated, because where a word appears says a lot about whether
-- the memory is actually about it. A note titled "Sikkim" is a better answer for "sikkim" than one
-- that mentions the word once in a long body, and ts_rank_cd's default weights ({0.1, 0.2, 0.4, 1.0}
-- for D, C, B, A) already encode that ordering.
--
-- 'english' is a deliberate simplification: it buys stemming, so "photos" finds "photo" and "running"
-- finds "ran", at the cost of applying English rules to text that is not English. Other scripts still
-- index and match as whole words, they just are not stemmed. Detecting a language per memory belongs
-- with the importers that will bring in bulk foreign-language text.
ALTER TABLE memories
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'C')
    ) STORED;

CREATE INDEX ix_memories_search_vector ON memories USING GIN (search_vector);

-- Turns whatever a person typed into a tsquery, and is the only place raw input meets the text search
-- engine.
--
-- Two behaviours, chosen by whether the input contains a double quote:
--
--   * Unquoted input is treated as a prefix search over every word, so "birth" finds "birthday" while
--     the user is still typing. This is the common case and the one that has to feel responsive.
--   * Quoted input is handed to websearch_to_tsquery, which gives exact phrase matching along with the
--     search-engine syntax people already expect from a quoted query.
--
-- The unquoted path deliberately tokenises with to_tsvector rather than by splitting on whitespace.
-- That reuses the same parser, dictionary and stemmer as the indexed column, so a query cannot match
-- differently from how the document was indexed. It also means no fragment of user input is ever
-- interpreted as tsquery syntax: characters like & | ! ( ) : * are discarded as punctuation during
-- tokenisation, and each surviving lexeme is quoted before being reassembled.
--
-- Returns NULL when the input holds no searchable words at all — empty, punctuation only, or nothing
-- but stop words. NULL is correct rather than convenient: `search_vector @@ NULL` is not true, so such
-- a query matches nothing instead of silently matching everything.
CREATE FUNCTION memory_search_query(raw text) RETURNS tsquery
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    RETURNS NULL ON NULL INPUT
AS $$
    SELECT CASE
        WHEN strpos(raw, '"') > 0 THEN websearch_to_tsquery('english', raw)
        ELSE (
            SELECT to_tsquery('english', string_agg(quote_literal(lexeme) || ':*', ' & '))
            FROM unnest(to_tsvector('english', raw))
        )
    END;
$$;

COMMENT ON FUNCTION memory_search_query(text) IS
    'Parses user-supplied search text into a tsquery: prefix matching by default, exact phrase matching when quoted.';
