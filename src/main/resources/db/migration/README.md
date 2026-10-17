# Database migrations

Each developer runs the backend locally with the `local` profile, but points it at the team's shared Supabase **development** project. The `prod` profile points at the separate production database.

Flyway runs versioned SQL migrations from this directory against the database selected by the active profile. Once a migration is merged and the backend starts, Flyway applies it to that shared database and records it in `flyway_schema_history`.

Use names such as `V202610061200__create_initial_schema.sql` and add a new migration for each schema change. Coordinate changes through Git; do not edit or reuse a migration after it has been applied to a shared database. Hibernate schema generation is disabled; Flyway owns schema changes. Do not point local development at the production database.

## First application of V1

Supabase may already contain objects such as `public.rls_auto_enable`, even when there are no application tables. Flyway treats this as a non-empty schema and requires a baseline before its first migration. The development project was checked on 2026-10-14: no application tables and no Flyway history table existed. Preserve Supabase's existing objects.

For the initial development setup, `application-local.properties` temporarily enabled `spring.flyway.baseline-on-migrate=true`; the shared configuration sets `spring.flyway.baseline-version=0`. This creates a history starting at version 0 and then runs V1. Do not use baseline version 1: it would skip V1. Other environments keep auto-baselining disabled by default and must be inspected separately before enabling it.

V1 was successfully applied to the shared development Supabase project on 2026-10-14 at 01:20 (Asia/Saigon). A read-only check confirmed `flyway_schema_history` records version 1 with `success=true` and all 30 application tables exist. Local auto-baselining is now disabled again. V1 is applied and must remain unchanged; use a new versioned migration for every subsequent schema change.

`V1__init_schema.sql` currently defines 30 application tables, including `ai_sessions`. It expects Supabase's existing `auth.users` table. From the backend directory, run `./mvnw.cmd spring-boot:run` on Windows with a configured local profile. Flyway applies V1 only if it has not already run. After this first migration, add new versioned migrations for subsequent schema changes.

Local credentials remain in the ignored `application-local.properties`. AI models are disabled by default so database/business development does not need a Gemini key. Chat can be enabled later with `SPRING_AI_MODEL_CHAT=google-genai`; image/embedding integrations also require revisiting the excluded connection auto-configurations. No AI feature or session API is enabled merely by creating the table.
