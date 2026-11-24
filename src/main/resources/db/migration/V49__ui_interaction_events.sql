-- Sự kiện tương tác của Guest/Customer trên storefront và surface A2UI.
-- Dùng để tổng hợp hồ sơ giao diện (thói quen hiển thị, component hay dùng, back nhanh) cho Generate UI.
-- id do client sinh (eventId) để gửi lại không bị ghi trùng. Giữ tối đa 90 ngày (job dọn dữ liệu xóa bản cũ).
CREATE TABLE public.ui_interaction_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES public.users (id) ON DELETE CASCADE,
    anon_id VARCHAR(64),
    event_type VARCHAR(40) NOT NULL,
    route VARCHAR(255) NOT NULL,
    surface_id VARCHAR(64),
    component VARCHAR(64),
    target VARCHAR(100),
    value JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ui_interaction_events_actor CHECK (user_id IS NOT NULL OR anon_id IS NOT NULL),
    CONSTRAINT ck_ui_interaction_events_type CHECK (event_type IN (
        'page_view', 'page_leave', 'quick_back', 'search', 'add_to_cart', 'assistant_prompt',
        'a2ui_render', 'a2ui_action', 'a2ui_rejected'
    )),
    CONSTRAINT ck_ui_interaction_events_route CHECK (route LIKE '/%'),
    CONSTRAINT ck_ui_interaction_events_value CHECK (JSONB_TYPEOF(value) = 'object')
);

CREATE INDEX ix_ui_interaction_events_user_time
    ON public.ui_interaction_events (user_id, occurred_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX ix_ui_interaction_events_anon_time
    ON public.ui_interaction_events (anon_id, occurred_at DESC) WHERE anon_id IS NOT NULL;
CREATE INDEX ix_ui_interaction_events_received
    ON public.ui_interaction_events (received_at);

ALTER TABLE public.ui_interaction_events ENABLE ROW LEVEL SECURITY;
