# Trail Journal

A personal Android journal for indoor walking, outdoor walking, and hiking. Version **0.9.1** includes a focused Home dashboard, compact activity cards, simpler navigation and a grouped entry form with an always-reachable Save button. A forest palette, adaptive launcher icon, launch screen and system dark mode give the app a consistent identity. Health settings use separate connection, sharing and import sections. Workout selection, map confirmation and diagnostics have fixed primary actions; sync history summarizes waiting, attention and completed operations. Durable Health Connect sync, backup recovery and diagnostics are retained.

**Validation:** 146 Kotlin/JUnit tests pass. Android production, debug-preview and instrumentation sources compile directly with real cached dependencies and the Compose compiler. Android resources compile and link with API 36 aapt2. These checks do not replace a full Gradle/KSP/APK build or visual/device testing, which remain pending because Gradle cannot create its daemon socket here. See `docs/VALIDATION.md`.

## Everyday use

- **Home:** this week's activity count, kilometres and time logged; all-time totals; recent entries; first-use guidance and a compact backup reminder. Use **Log activity** to start an entry.
- **Journal:** search names, venues and notes; combine activity-type and date-period filters; reset all filters in one action; browse results grouped by month.
- **Duration:** use 15/30/45/60-minute presets or an hours/minutes picker; lists display values such as 1 h 30 min. Saved data remains in compatible whole minutes.
- **Health apps:** preview/import eligible workouts and explicitly share journal activities through Android Health Connect. Set a start time and enable **Automatically share & update** to queue new activities and edits. Each card shows sync status, last success, errors and Retry. Compatible watch companion apps must also support Health Connect.
- **Log/Edit:** grouped basics, date/time and optional details, with Save and Cancel in a fixed bottom bar. Entries default to today and the last successfully saved activity type. Distance, duration and optional real start time are the main inputs; optional name, venue, map pin, effort, and notes are collapsed for quick entry.
- **Protect work:** leaving, clearing, or switching away from changed input asks before discarding. Small drafts and their original baseline use Android saved state.
- **Undo deletion:** deleting shows an Undo snackbar instead of a confirmation dialog. Undo restores the same ID and metadata without overwriting a conflicting entry, and supersedes a queued Health Connect deletion.
- **Back up and restore:** choose a JSON file through Android's system document picker. **Journal tools** (top-right menu) shows the last recorded backup, newer unbacked changes, optional seven-day reminders and Recovery guide. Restore validates/previews before confirmation and keeps existing IDs unchanged.
- **Sync history & diagnostics:** open from **Journal tools** or the health panel to inspect the latest operation per activity, including pending/completed removals, failure guidance and Retry. Preview the exact diagnostic text before choosing where to save it.
- **Accessibility:** follow system light/dark mode; action rows wrap, long dialogs and map coordinates scroll, fields expose errors to accessibility services, and repeated activity actions have contextual labels.
- **Maps:** choose a point or enter coordinates, save/change/remove pins, and reopen saved locations. No API key or GPS permission is required for this online map integration.

- **Activity actions:** use each card's **More options** menu for Edit, Share, Map and Delete. Expand **Details** for timing and sync metadata; failed sync keeps its Retry action visible.
- **Tools:** the top-right menu groups health permissions/import, automatic sharing, sync history, diagnostics and backup/recovery controls.

## UI previews

Open `app/src/debug/java/dev/michelle/trails/JournalPreviews.kt` in Android Studio and select Split/Design. Previews cover Home in light/dark mode, the Journal, the entry form, first use, health settings, workout selection, sync history, backups, diagnostics and large text. Fixtures are debug-only synthetic examples; they never populate the real journal. Previews compile here but have not been rendered or visually reviewed on a device.

UI responsibilities are split between the screen shell, Home content, History content, shared components, cards, editor and Tools sheet. Presentation/domain contracts remain independent of Compose. The redesign keeps Room schema 4 and backup format 2 unchanged.

## Open and run

Use Android Studio, JDK 17, Android SDK Platform 36, and compatible Build Tools. Open this folder, sync Gradle, select `app`, and run on API 26+. The first sync needs internet access.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:installDebug
```

On Windows use `gradlew.bat`. If ZIP extraction loses executable permission, run `chmod +x gradlew`. Terminal builds need `ANDROID_HOME` or an untracked `local.properties` SDK path.

With an emulator or device connected:

```sh
./gradlew :app:connectedDebugAndroidTest
python3 scripts/check_legacy_migration.py
```

**Upgrade without uninstalling:** keep application ID `dev.michelle.trail`, database filename `walks.db`, and the same signing key. Version code/name are **12 / 0.9.1**. Room schema is now **4** with a non-destructive 3→4 migration for nullable timing fields and sync tables; 1→2→3→4 migrations remain registered. Known 0.5.2 share timing and consent are imported once from preferences. No start time is guessed for older entries.

## Project structure and SOLID boundaries

The project has one Gradle module, `app`. All production Kotlin sources live under:

`app/src/main/java/dev/michelle/trails/`

| Package | Responsibility |
| --- | --- |
| Root package | Compose screens, ViewModel, Android adapters, application wiring |
| `domain` | Values, validation, use cases, filtering and platform-neutral contracts |
| `presentation` | State, events, navigation, draft protection and health/journal controllers |
| `data` | Room entities, DAO, repository, migrations and atomic import |
| `backup` | Strict versioned JSON adapter |
| `maps` | Native map rendering and lifecycle adapter |
| `health` | Health Connect SDK, permission and settings adapters |

Unit tests are in `app/src/test/java/dev/michelle/trails/`; device tests are in `app/src/androidTest/java/dev/michelle/trails/`. Room schema exports are in `app/schemas/`.

The Kotlin package/Android namespace is `dev.michelle.trails`. The installed application ID deliberately remains `dev.michelle.trail`, preserving existing app data, preferences and Health Connect ownership. The database stays `walks.db`, migrated to schema version 4.

A single build module does not require a single responsibility for every class. The packages retain small interfaces and constructor injection. The import-boundary check protects domain/presentation from depending on Android and the concrete adapters:

```sh
python3 scripts/check_architecture.py
```

## Backup behavior

Open **Journal tools** (top-right menu), use **Back up**, choose a destination, and wait for confirmation. Use **Restore**, select a Trail Journal JSON file, review new/existing counts, and confirm. Nothing is deleted or replaced by restore. The Tools sheet shows the last recorded export time, its activity count and whether current contents differ. In-app reminders can be switched off or snoozed for seven days; they do not send notifications. The receipt records an export, not continued availability of its external file. Open **Recovery guide** for changing phones/reinstalling and Health Connect limitations. A duplicate ID within a file or malformed entry rejects the whole preview. Repeat imports safely skip existing IDs. Limits: 10 MiB UTF-8 and 10,000 entries per backup. New backups use format version 2 and include activity start time/time zone. Version-1 backups still restore. Sync consent and pending jobs are not exported. Backups contain plain-text notes and location pins; choose the destination accordingly.

Undo is session-only and covers the most recent deletion while its action remains available. It is not a replacement for a saved backup. If an undo insert fails, a Retry undo action is offered with the error.

## Pinned build baseline

Gradle 8.13, AGP 8.11.1, Kotlin/Compose compiler 2.0.21, Compose BOM 2025.04.01, Room 2.7.1, KSP 2.0.21-1.0.28, Lifecycle 2.9.0, Activity 1.10.1, coroutines 1.9.0, Gson 2.11.0, Health Connect 1.1.0, and MapLibre Native Android 11.8.0. These are explicit integration pins, not a latest-release claim. Full toolchain compatibility still needs the documented Android build.

## Health apps and smartwatches

Open **Journal tools → Health apps & smartwatches**. Allow workout/distance reading, preview recent sessions, select the copies you want, and import. Source apps must provide eligible data to Health Connect; this is not universal watch pairing. Imported sessions use elapsed time including pauses, rounded up to whole minutes, with source/timing notes.

In **Health apps & smartwatches**, enable **Automatically share & update** after granting exercise/distance write permissions. Saving a journal activity with a real start time queues its first share; edits queue updates to the same client IDs. Unknown start times stay local and can be entered in the form or one-time Share dialog. The original time zone is retained. Cards show Not shared, Needs start time, Pending, Automatic updates off, Needs permission, Sync failed or Synced to Health Connect, plus last success and per-entry Retry.

Journal mutations and sync jobs commit in one Room transaction. Pending work survives process death. Provider acknowledgements match a revision, so a stale response cannot acknowledge a newer edit or Undo. Transient errors use bounded exponential backoff. Android JobScheduler runs deferred writes when the system permits; foreground resume and Check for updates also reconcile. No network or background-read permission is needed to write these local Health Connect records. Force-stop and Android power restrictions can defer work until the app is reopened.

Deleting a previously requested export queues removal of this app's exercise/distance records with a 30-second grace period. Undo supersedes the deletion or queues a restoring upsert if deletion already ran. Imported records are never written back or deleted from their original provider. Other apps control their own retained copies and refresh timing. Daily steps and calorie estimates are not implemented.

Apple Health cannot be accessed directly by this Android app; it requires a separate iOS/HealthKit companion, which is not included. See `docs/HEALTH_CONNECT.md` for precise compatibility, privacy, and device-testing requirements.

## Current limits

Completed activities are entered manually. Live GPS/steps, route recording, place search, cloud sync, charts, custom goals, and CSV export are not implemented. The base map needs internet; manual coordinate entry and the journal work offline. Android saved-state restoration is conditional, not a durable draft backup or exactly-once submission guarantee across process death. History/summary currently load the journal into memory rather than using paging.

See `docs/RELEASE.md`, `docs/SUPPORT.md`, `docs/HEALTH_CONNECT.md`, `docs/ARCHITECTURE.md`, `docs/BACKUP.md`, `docs/MAPS.md`, `docs/MIGRATION.md`, and `docs/MANUAL_TESTS.md`. This is an independent personal portfolio project; no company implementation was used.

## Release checks

Compile and target SDK are 36. CI installs Platform/Build Tools 36, runs unit tests and release lint, builds debug/test APKs plus an unsigned release APK/bundle, and checks native ELF/APK ZIP alignment. It uploads reports, schemas and unsigned artifacts for review. Device instrumentation remains a manually triggered API-36 workflow. These workflow changes have not been executed remotely. Signing and publishing are separate release steps; see `docs/RELEASE.md`.


## Portfolio description

Use original screenshots from your own test data after completing device validation. This is a personal portfolio project, not a claim of production deployment or universal smartwatch support.
