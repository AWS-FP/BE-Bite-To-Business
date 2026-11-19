-- Không tự xóa dữ liệu email chưa gắn với customer; cần đối chiếu trước khi chuyển.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.newsletter_subscriptions s
        WHERE NOT EXISTS (SELECT 1 FROM public.users u WHERE lower(u.email) = s.email AND u.role = 'CUSTOMER')
    ) THEN
        RAISE EXCEPTION 'Newsletter migration stopped: subscriptions exist without a matching customer';
    END IF;
END $$;

ALTER TABLE public.users ADD COLUMN newsletter_subscribed BOOLEAN NOT NULL DEFAULT FALSE,
                         ADD COLUMN newsletter_consented_at TIMESTAMPTZ;
UPDATE public.users u SET newsletter_subscribed = s.is_active, newsletter_consented_at = s.consented_at
FROM public.newsletter_subscriptions s WHERE lower(u.email) = s.email AND u.role = 'CUSTOMER';

ALTER TABLE public.newsletter_promotion_deliveries ADD COLUMN customer_id UUID;
UPDATE public.newsletter_promotion_deliveries d SET customer_id = u.id
FROM public.newsletter_subscriptions s JOIN public.users u ON lower(u.email) = s.email AND u.role = 'CUSTOMER'
WHERE d.subscription_id = s.id;
ALTER TABLE public.newsletter_promotion_deliveries ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE public.newsletter_promotion_deliveries DROP CONSTRAINT uq_newsletter_promotion_delivery;
ALTER TABLE public.newsletter_promotion_deliveries DROP COLUMN subscription_id;
ALTER TABLE public.newsletter_promotion_deliveries ADD CONSTRAINT fk_promotion_email_customer FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE public.newsletter_promotion_deliveries ADD CONSTRAINT uq_promotion_email_customer UNIQUE(customer_id, promotion_id);
ALTER TABLE public.newsletter_promotion_deliveries RENAME TO promotion_email_deliveries;
DROP TABLE public.newsletter_subscriptions;
