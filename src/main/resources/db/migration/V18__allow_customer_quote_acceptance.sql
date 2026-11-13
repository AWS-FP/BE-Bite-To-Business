ALTER TABLE public.quotes DROP CONSTRAINT ck_quotes_reviewed_state;
ALTER TABLE public.quotes ADD CONSTRAINT ck_quotes_reviewed_state CHECK (
    status NOT IN ('APPROVED', 'REJECTED')
    OR (reviewed_revision IS NOT NULL AND reviewed_revision = revision AND reviewed_at IS NOT NULL)
);
