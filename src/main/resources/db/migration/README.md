# Database migrations

Each developer runs the backend locally with the `local` profile, but points it at the team's shared Supabase **development** project. The `prod` profile points at the separate production database.

Flyway runs versioned SQL migrations from this directory against the database selected by the active profile. Once a migration is merged and the backend starts, Flyway applies it to that shared database and records it in `flyway_schema_history`.

Use names such as `V202610061200__create_initial_schema.sql` and add a new migration for each schema change. Coordinate changes through Git; do not edit or reuse a migration after it has been applied to a shared database. Hibernate schema generation is disabled; Flyway owns schema changes. Do not point local development at the production database.
