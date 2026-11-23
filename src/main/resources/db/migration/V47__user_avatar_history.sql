-- Persist avatar choices for Customer and workspace employees.
-- Frontend localStorage history was browser-only and cannot authorize a restore.
CREATE TABLE public.user_avatar_history (
    user_id UUID NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    avatar_url TEXT NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, avatar_url),
    CONSTRAINT ck_user_avatar_history_https CHECK (avatar_url LIKE 'https://%')
);

CREATE INDEX ix_user_avatar_history_recent
    ON public.user_avatar_history (user_id, last_used_at DESC);

-- Existing profile images remain selectable after deploying the migration.
INSERT INTO public.user_avatar_history (user_id, avatar_url, last_used_at)
SELECT id, avatar_url, COALESCE(updated_at, NOW())
FROM public.users
WHERE avatar_url IS NOT NULL AND avatar_url LIKE 'https://%';

-- Backend is the only API permitted to read/write this history.
ALTER TABLE public.user_avatar_history ENABLE ROW LEVEL SECURITY;
