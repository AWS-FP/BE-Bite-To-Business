-- Keep legacy orders on their original inventory model; only new combo lines expand stock.
ALTER TABLE public.order_items ADD COLUMN component_inventory boolean NOT NULL DEFAULT false;

CREATE TABLE public.quote_combo_items (
    quote_item_id uuid NOT NULL REFERENCES public.quote_items(id) ON DELETE CASCADE,
    product_id uuid NOT NULL REFERENCES public.products(id) ON DELETE RESTRICT,
    quantity_per_combo numeric(12,3) NOT NULL CHECK (quantity_per_combo > 0),
    unit_code varchar(20) NOT NULL,
    PRIMARY KEY (quote_item_id, product_id)
);
ALTER TABLE public.quote_combo_items ENABLE ROW LEVEL SECURITY;

-- Internal views, not exposed through the Supabase public API.
CREATE SCHEMA checkout_stock;
REVOKE ALL ON SCHEMA checkout_stock FROM PUBLIC;
CREATE VIEW checkout_stock.components AS
    SELECT p.id AS sellable_id, p.id AS product_id, 1::numeric AS factor, p.unit_code
    FROM public.products p
    WHERE p.status = 'PUBLISHED' AND p.unit_code <> 'COMBO'
    UNION ALL
    SELECT p.id, ci.product_id, ci.quantity, ingredient.unit_code
    FROM public.products p
    JOIN public.combos c ON c.product_id = p.id
    JOIN public.combo_items ci ON ci.combo_id = p.id
    JOIN public.products ingredient ON ingredient.id = ci.product_id
    WHERE p.status = 'PUBLISHED' AND p.unit_code = 'COMBO'
      AND p.min_order_quantity = 1 AND p.quantity_step = 1
      AND NOT EXISTS (
        SELECT 1 FROM public.combo_items other
        JOIN public.products ip ON ip.id = other.product_id
        WHERE other.combo_id = p.id AND (ip.status <> 'PUBLISHED'
          OR ip.unit_code = 'COMBO' OR other.quantity <= 0
          OR mod(other.quantity, ip.quantity_step) <> 0)
      );

CREATE VIEW checkout_stock.availability AS
    SELECT c.sellable_id AS product_id,
           min(CASE WHEN p.unit_code = 'COMBO'
               THEN floor(greatest(coalesce(i.available_quantity,0),0) / c.factor)
               ELSE greatest(coalesce(i.available_quantity,0),0) END) AS available_quantity
    FROM checkout_stock.components c
    JOIN public.products p ON p.id = c.sellable_id
    LEFT JOIN public.inventory i ON i.product_id = c.product_id
    GROUP BY c.sellable_id;

CREATE VIEW checkout_stock.order_components AS
    SELECT oi.id AS order_item_id, oi.order_id, oi.product_id, 1::numeric AS factor
    FROM public.order_items oi WHERE NOT oi.component_inventory
    UNION ALL
    SELECT oi.id, oi.order_id, ci.product_id, ci.quantity_per_combo
    FROM public.order_items oi JOIN public.order_combo_items ci ON ci.order_item_id = oi.id
    WHERE oi.component_inventory;
