CREATE TABLE public.ai_request_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp()
);

CREATE INDEX idx_ai_request_usage_user_time
    ON public.ai_request_usage (user_id, requested_at);

ALTER TABLE public.ai_request_usage ENABLE ROW LEVEL SECURITY;
