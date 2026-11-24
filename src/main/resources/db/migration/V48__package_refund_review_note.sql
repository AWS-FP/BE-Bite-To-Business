-- E-commerce ghi lý do khi duyệt/từ chối yêu cầu hủy package của khách.
ALTER TABLE public.package_refunds ADD COLUMN review_note TEXT;

CREATE INDEX IF NOT EXISTS ix_package_refunds_status_created
    ON public.package_refunds (status, created_at DESC);
