-- Ask questions are natural language ("What happened on the Sikkim trip?"), not keyword bags.
-- Search's AND of every non-stopword would require the verb "happened" to appear in the note.
-- Ask keeps the same tokeniser and prefix matching, but ORs the terms so any shared content word
-- can surface a candidate; ranking and the distance ceiling still decide what is kept.

CREATE FUNCTION memory_ask_query(raw text) RETURNS tsquery
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    RETURNS NULL ON NULL INPUT
AS $$
    SELECT CASE
        WHEN strpos(raw, '"') > 0 THEN websearch_to_tsquery('english', raw)
        ELSE (
            SELECT to_tsquery('english', string_agg(quote_literal(lexeme) || ':*', ' | '))
            FROM unnest(to_tsvector('english', raw))
        )
    END;
$$;

COMMENT ON FUNCTION memory_ask_query(text) IS
    'Parses an Ask question into a tsquery: OR of prefix terms (quoted phrases still exact).';
