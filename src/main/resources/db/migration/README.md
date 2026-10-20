# Database migrations

Each developer runs the backend locally with the `local` profile, but points it at the team's shared Supabase **development** project. The `prod` profile points at the separate production database.

Flyway runs versioned SQL migrations from this directory against the database selected by the active profile. Once a migration is merged and the backend starts, Flyway applies it to that shared database and records it in `flyway_schema_history`.

Use sequential names such as `V1__init_schema.sql`, `V2__allow_optional_user_profile_fields.sql`, and `V3__create_product_details.sql`, following the SWP391 project. The version and description are separated by two underscores. Add the next unused version for each schema change and coordinate version numbers through Git. Do not rename, edit, or reuse a migration after it has been applied to a shared database. Hibernate schema generation is disabled; Flyway owns schema changes. Do not point local development at the production database.

Current migrations:

- `V1__init_schema.sql`: the initial schema, already applied.
- `V2__allow_optional_user_profile_fields.sql`: allow optional profile fields for Admin and Staff.
- `V3__create_product_details.sql`: add the four-section product details table, VAT invoice records (`invoices`), product bundles (`combos`, `combo_items`), purchased combo components (`order_combo_items`), and AI conversation messages (`ai_messages`). Each Order has at most one invoice; buyer details, amounts and invoice lines reuse the immutable snapshots in `orders` and `order_items`.

V2 and V3 were renamed from timestamp-based filenames before being run, as confirmed by the user. V3 was subsequently extended with invoices, combos, purchased combo components and AI messages at the user's request, before migration. The invoice schema does not implement PDF generation or email delivery by itself.

Combos reuse `products` for their SKU, bilingual content, images, publication status and promotion. Their own prices and volume tiers use `product_prices` and `product_price_tiers`. V3 adds the `COMBO` selling unit without changing V1. `combos` stores only the product reference and optional positive serving count; `combo_items` stores each component and its quantity per one combo, using the component's selling unit. The composite primary key prevents duplicate components, the check constraint prevents direct self-reference, and a reverse index supports finding combos containing a product.

The combo service is not implemented yet. It must atomically save the combo, prices and components; enforce whole-combo quantities, at least one component before publication, no nested combos, valid component quantities and availability; and use the combo's own sale price rather than additionally applying component sales. Stock must be reserved/released against components, including any quantities of the same SKU bought separately. Combos have no independently entered inventory. RLS is enabled for the new tables, with access through the backend as for existing application tables.

`order_combo_items` stores the purchased component SKU, bilingual name, selling unit and quantity per one combo, keyed by `(order_item_id, product_id)`. These are intentional historical snapshots, following `order_items`; they do not change when catalog data or combo composition changes. Total component quantities come from the purchased combo count, or the assigned count in `package_items` for one package. Component prices are not charged again. The backend must consistently read/lock combo composition, create these snapshots and reserve stock in the order transaction, enforce combo-only parent lines and whole-combo package allocations, and preserve snapshots after confirmation/cancellation. The snapshot schema is present; checkout, fulfillment and stock services are not implemented by the migration.

`ai_messages` stores `USER`/`ASSISTANT` text messages linked to `ai_sessions`, with a positive per-session sequence number, context version and creation time. The unique `(session_id, sequence_no)` index serves ordered history and pagination. The backend must lock the session when allocating sequence numbers, enforce session ownership/expiry, update session context/version/activity in the appropriate transaction, and reject stale UI/actions by checking context versions. Deleting a session also deletes its messages. Only completed text messages are in scope; UI specs, tool-call records and long-term memory are deferred. No AI/session API or model integration is implemented by creating this table.

## First application of V1

Supabase may already contain objects such as `public.rls_auto_enable`, even when there are no application tables. Flyway treats this as a non-empty schema and requires a baseline before its first migration. The development project was checked on 2026-10-14: no application tables and no Flyway history table existed. Preserve Supabase's existing objects.

For the initial development setup, `application-local.properties` temporarily enabled `spring.flyway.baseline-on-migrate=true`; the shared configuration sets `spring.flyway.baseline-version=0`. This creates a history starting at version 0 and then runs V1. Do not use baseline version 1: it would skip V1. Other environments keep auto-baselining disabled by default and must be inspected separately before enabling it.

V1 was successfully applied to the shared development Supabase project on 2026-10-14 at 01:20 (Asia/Saigon). A read-only check confirmed `flyway_schema_history` records version 1 with `success=true` and all 30 application tables exist. Local auto-baselining is now disabled again. V1 is applied and must remain unchanged; use a new versioned migration for every subsequent schema change.

`V1__init_schema.sql` currently defines 30 application tables, including `ai_sessions`. It expects Supabase's existing `auth.users` table. From the backend directory, run `./mvnw.cmd spring-boot:run` on Windows with a configured local profile. Flyway applies V1 only if it has not already run. After this first migration, add new versioned migrations for subsequent schema changes.

Local credentials remain in the ignored `application-local.properties`. AI models are disabled by default so database/business development does not need a Gemini key. Chat can be enabled later with `SPRING_AI_MODEL_CHAT=google-genai`; image/embedding integrations also require revisiting the excluded connection auto-configurations. No AI feature or session API is enabled merely by creating the table.
