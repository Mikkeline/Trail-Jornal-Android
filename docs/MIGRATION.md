# SQLite/Room migration history

## Preserve installation identity

Keep the application ID `dev.michelle.trail`, the database name `walks.db`, and the signing key when updating an installed app. Version code is now 6; Room schema version is 3. Uninstalling or clearing app data removes the original database before migration can run.

## Why rebuild the table

The version-1 `SQLiteOpenHelper` schema used `id TEXT PRIMARY KEY` without an explicit NOT NULL constraint. A Room entity with a non-null String ID expects NOT NULL. Adding only the new columns would not correct that difference.

`MIGRATION_1_2` creates `walks_new` with the final schema, copies the six original fields exactly, supplies `OUTDOOR_WALK`, an empty location, and null effort, then swaps the tables. Room runs migration in its upgrade transaction and validates the resulting schema. No destructive fallback is configured.

A corrupt legacy row with a null ID fails the new constraint. The migration aborts rather than inventing IDs or discarding entries. The desktop SQL test checks rollback against this case; the Android test is the authoritative check for Room opening and schema validation.

## Verification included

- `LegacyMigrationTest` creates a real version-1 helper database, opens it through Room, checks every original field/default, edits and deletes the migrated entry, and verifies duplicate inserts do not replace it.
- `scripts/check_legacy_migration.py` reads the actual SQL constants from `TrailDatabase.kt` and runs three desktop SQLite tests. It does not verify Android SQLite or Room-generated validation.
- KSP exports the current schema into `app/schemas` on the first successful build. Commit that generated JSON. No schema identity hash has been guessed in this package.

Run the device tests and inspect the generated schema before claiming migration validation. Retain exported schemas when adding version 4 or later.

## Version 2 to 3 — optional map coordinates

`MIGRATION_2_3` adds nullable REAL `latitude` and `longitude` columns. Existing records keep their metadata and start without a pin. Both migrations are registered, so the supported paths are 1→2→3 and 2→3. New version-3 databases have the same columns through Room's entity schema.

The domain stores coordinates as an optional validated `GeoPoint`; zero coordinates are valid and are never used as a missing-value sentinel. Both database columns must be null for no pin. A partial pair is reported as corrupt data by the adapter rather than silently dropped. Removing a pin sets both columns to null. Editing uses the same record ID.

`MapMigrationTest` creates a version-2 fixture and checks defaults, pin persistence across reopening, removal, and corrupt-pair reporting. These Android tests still need execution. The six desktop SQL tests pass but do not validate Room-generated schemas. KSP will export version 3 on the first successful build; keep existing version-2 exports as well.


## Package/module move — app version 0.5.1

The source namespace becomes dev.michelle.trails in a single app module. The installed application ID remains dev.michelle.trail and walks.db stays version 3. Existing generated schema JSON was moved without changing its SQL or identity hash. PackageMoveDatabaseTest constructs the prior version-3 schema and identity table from that retained export, then opens it using the relocated Room class. Run this device test before claiming upgrade validation; no destructive fallback was added.

## Version 4 — 0.6.0

Migration 3→4 adds nullable startTime/zoneId columns and creates health_sync and health_sync_settings. It does not delete/rebuild journal rows. Database filename and application ID are unchanged. Existing 1→2 and 2→3 migrations remain chained. The Room adapter imports known 0.5.2 share timing/consent preferences once after schema migration. Unknown timing stays null. Gradle/KSP exports schema 4 when the project is built; existing schema exports 2/3 are retained unchanged.
