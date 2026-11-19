-- A package, item or payment cannot be moved to a different parent after creation.
CREATE FUNCTION public.prevent_parent_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF to_jsonb(NEW)->>TG_ARGV[0] IS DISTINCT FROM to_jsonb(OLD)->>TG_ARGV[0] THEN
        RAISE EXCEPTION 'Changing the parent of % is not allowed', TG_TABLE_NAME USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER lock_order_package_parent BEFORE UPDATE OF order_id ON public.order_packages FOR EACH ROW EXECUTE FUNCTION public.prevent_parent_change('order_id');
CREATE TRIGGER lock_order_item_parent BEFORE UPDATE OF order_id ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.prevent_parent_change('order_id');
CREATE TRIGGER lock_payment_parent BEFORE UPDATE OF order_id ON public.payments FOR EACH ROW EXECUTE FUNCTION public.prevent_parent_change('order_id');
CREATE TRIGGER lock_quote_package_parent BEFORE UPDATE OF quote_id ON public.quote_packages FOR EACH ROW EXECUTE FUNCTION public.prevent_parent_change('quote_id');
CREATE TRIGGER lock_quote_item_parent BEFORE UPDATE OF quote_id ON public.quote_items FOR EACH ROW EXECUTE FUNCTION public.prevent_parent_change('quote_id');

ALTER TABLE public.package_items DROP CONSTRAINT fk_package_items_item, DROP CONSTRAINT fk_package_items_package;
ALTER TABLE public.package_items DROP COLUMN order_id;
ALTER TABLE public.package_items ADD CONSTRAINT fk_package_item_item FOREIGN KEY (order_item_id) REFERENCES public.order_items(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_package_item_package FOREIGN KEY (order_package_id) REFERENCES public.order_packages(id) ON DELETE RESTRICT;

CREATE FUNCTION public.check_package_item_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.order_packages p JOIN public.order_items i ON i.order_id = p.order_id
                   WHERE p.id = NEW.order_package_id AND i.id = NEW.order_item_id) THEN
        RAISE EXCEPTION 'Package and item must belong to the same order' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER check_package_item_order BEFORE INSERT OR UPDATE ON public.package_items FOR EACH ROW EXECUTE FUNCTION public.check_package_item_order();

ALTER TABLE public.quote_package_items DROP CONSTRAINT fk_quote_package_items_item, DROP CONSTRAINT fk_quote_package_items_package;
ALTER TABLE public.quote_package_items DROP COLUMN quote_id;
ALTER TABLE public.quote_package_items ADD CONSTRAINT fk_quote_package_item_item FOREIGN KEY (quote_item_id) REFERENCES public.quote_items(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quote_package_item_package FOREIGN KEY (quote_package_id) REFERENCES public.quote_packages(id) ON DELETE CASCADE;

CREATE FUNCTION public.check_quote_package_item_quote() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.quote_packages p JOIN public.quote_items i ON i.quote_id = p.quote_id
                   WHERE p.id = NEW.quote_package_id AND i.id = NEW.quote_item_id) THEN
        RAISE EXCEPTION 'Package and item must belong to the same quote' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER check_quote_package_item_quote BEFORE INSERT OR UPDATE ON public.quote_package_items FOR EACH ROW EXECUTE FUNCTION public.check_quote_package_item_quote();

ALTER TABLE public.package_refunds DROP CONSTRAINT fk_package_refunds_package, DROP CONSTRAINT fk_package_refunds_payment;
ALTER TABLE public.package_refunds DROP COLUMN order_id;
ALTER TABLE public.package_refunds ADD CONSTRAINT fk_refund_package FOREIGN KEY (order_package_id) REFERENCES public.order_packages(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES public.payments(id) ON DELETE RESTRICT;

CREATE FUNCTION public.check_refund_payment_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.order_packages p JOIN public.payments pay ON pay.order_id = p.order_id
                   WHERE p.id = NEW.order_package_id AND pay.id = NEW.payment_id) THEN
        RAISE EXCEPTION 'Package and payment must belong to the same order' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER check_refund_payment_order BEFORE INSERT OR UPDATE ON public.package_refunds FOR EACH ROW EXECUTE FUNCTION public.check_refund_payment_order();
