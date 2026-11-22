-- Store per-customer notification choices. Existing notifications remain intact.
CREATE TABLE public.customer_notification_preferences (
    customer_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    quote_approval BOOLEAN NOT NULL DEFAULT TRUE,
    quote_expiry BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_eta BOOLEAN NOT NULL DEFAULT TRUE,
    sourcing_reply BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE public.customer_notification_preferences ENABLE ROW LEVEL SECURITY;

-- Optional subtype and event source for precise preference filtering and idempotence.
-- Legacy/general notifications remain visible, not guessed from ambiguous titles.
ALTER TABLE public.notifications
    ADD COLUMN event_kind VARCHAR(30),
    ADD COLUMN event_source_id UUID;
ALTER TABLE public.notifications
    ADD CONSTRAINT ck_notification_event_kind CHECK (
        event_kind IS NULL OR event_kind IN
        ('QUOTE_APPROVAL','QUOTE_EXPIRY','DELIVERY_ETA','SOURCING_REPLY')
    ),
    ADD CONSTRAINT ck_notification_event_pair CHECK (
        (event_kind IS NULL AND event_source_id IS NULL)
        OR (event_kind IS NOT NULL AND event_source_id IS NOT NULL)
    );
CREATE UNIQUE INDEX ux_notifications_event_once
    ON public.notifications(recipient_id,event_kind,event_source_id)
    WHERE event_kind IS NOT NULL;
