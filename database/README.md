# CodeGuardian AI — Database Scripts

## Files

| File | Purpose |
|---|---|
| `schema.sql` | Tables, indexes, triggers, views and stored procedures |
| `init_permissions.sql` | Seeds roles, permissions and the default admin user (**required**) |
| `init_data.sql` | Sample tasks, findings and reports for testing and demos (optional) |

The dated directories (`20260315/`, `20260529/`, `20260607/`) are earlier snapshots of these scripts,
kept for reference. Use the files in this directory for a new install.

The database name used throughout the application is **`code_guardian`** — this matches
`docker-compose.yml` and the `DB_URL` default in `application.yml`.

## The easy path

From the repository root:

```bash
docker-compose up -d
```

The Postgres container uses the `pgvector/pgvector:pg17` image and runs `schema.sql`,
`init_data.sql` and `init_permissions.sql` automatically on first start. Nothing else is needed.

## Manual setup

### 1. Create the database

```bash
createdb -U postgres code_guardian
```

Or from inside `psql`:

```sql
CREATE DATABASE code_guardian
    WITH OWNER = postgres
    ENCODING = 'UTF8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
```

### 2. Create the schema

```bash
psql -U postgres -d code_guardian -f schema.sql
```

### 3. Seed roles, permissions and the admin user (required)

```bash
psql -U postgres -d code_guardian -f init_permissions.sql
```

Default account: `admin` / `admin123`. **Change this password before deploying anywhere.**

### 4. Load sample data (optional)

```bash
psql -U postgres -d code_guardian -f init_data.sql
```

## Extensions

`schema.sql` creates three extensions:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- trigram indexes for fuzzy name search
CREATE EXTENSION IF NOT EXISTS btree_gin;    -- composite GIN indexes
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector, for RAG embeddings
```

`vector` is not part of a stock PostgreSQL install. Either use the `pgvector/pgvector` Docker image (as
`docker-compose.yml` does) or install the extension yourself — otherwise `schema.sql` fails at this step.

## Verifying the install

```sql
\dt                                    -- tables
\di                                    -- indexes
\dv                                    -- views
\df                                    -- functions

SELECT * FROM roles;
SELECT * FROM permissions;
SELECT * FROM v_user_permissions WHERE username = 'admin';

SELECT check_user_permission(1, 'ADMIN');   -- expected: true

SELECT * FROM v_task_statistics LIMIT 10;
SELECT * FROM v_finding_category_statistics;
```

## Clearing data

```sql
-- Wipe review data (destructive)
TRUNCATE TABLE review_reports CASCADE;
TRUNCATE TABLE findings CASCADE;
TRUNCATE TABLE review_tasks CASCADE;

-- Or drop completed tasks older than N days; 0 removes all completed tasks
SELECT * FROM cleanup_old_tasks(0);
```

## Notes

- Back up existing data before running these scripts against anything you care about.
- The database user needs privileges to create tables, indexes and extensions.
- PostgreSQL 15 or newer is recommended; the Docker image pins 17.

## Troubleshooting

**`ERROR: extension "vector" is not available`**

The `pgvector` extension is not installed. Use the `pgvector/pgvector:pg17` image, or install pgvector
for your PostgreSQL build and re-run `schema.sql`.

**Resetting the database**

```bash
dropdb -U postgres code_guardian && createdb -U postgres code_guardian
psql -U postgres -d code_guardian -f schema.sql
psql -U postgres -d code_guardian -f init_permissions.sql
```

With Docker, removing the volume has the same effect and re-runs the scripts on next start:

```bash
docker-compose down -v && docker-compose up -d
```

**Backing up**

```bash
pg_dump -U postgres -d code_guardian -F c -f code_guardian_backup.dump
```

Note that a full dump contains user rows, including password hashes — do not commit one to the
repository.
