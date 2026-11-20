CREATE TABLE public.ai_ui_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES public.ai_messages (id) ON DELETE CASCADE,
    ui_data JSONB NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ai_ui_results_data CHECK (JSONB_TYPEOF(ui_data) = 'object'),
    CONSTRAINT ck_ai_ui_results_schema_version CHECK (BTRIM(schema_version) <> '')
);

CREATE INDEX ix_ai_ui_results_message_created
    ON public.ai_ui_results (message_id, created_at);

CREATE TABLE public.ai_model_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES public.ai_sessions (id) ON DELETE SET NULL,
    model_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    input_tokens BIGINT,
    output_tokens BIGINT,
    duration_ms BIGINT,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ai_model_requests_model_id CHECK (BTRIM(model_id) <> ''),
    CONSTRAINT ck_ai_model_requests_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_model_requests_input_tokens CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_ai_model_requests_output_tokens CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_ai_model_requests_duration CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_ai_model_requests_error_code CHECK (error_code IS NULL OR BTRIM(error_code) <> '')
);

CREATE INDEX ix_ai_model_requests_session_created
    ON public.ai_model_requests (session_id, created_at DESC);

CREATE INDEX ix_ai_model_requests_created_at
    ON public.ai_model_requests (created_at DESC);

ALTER TABLE public.ai_ui_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_model_requests ENABLE ROW LEVEL SECURITY;
