# Architecture — 0.5.0

## Presentation and navigation

Compose renders `JournalUiState` and dispatches explicit events. Home, History, and Editor are separate destinations in the same small navigation state model. The Activity hosts an Android ViewModel, which supplies a lifecycle scope and platform adapters to the plain Kotlin `JournalController`. Rendering and formatting do not open databases or decode files.

The controller coordinates use cases; calculations such as date/type/text filtering remain in domain classes. Entries are ordered by activity date with a deterministic ID tie-break. The Home summary uses a Monday-based week. Search and period/type filters combine without mutating stored entries.

## Draft protection

`DraftStore` retains the draft and its baseline. Changing input sets the dirty flag through equality with that baseline. Navigation, clearing, or selecting another edit queues an action when the draft is dirty. Keep editing cancels the queued action; confirmed discard resets it and continues. Back uses the same navigation event. A restored changed draft opens the editor instead of silently disappearing behind Home.

The Android adapter stores primitive values in Bundles, including optional pin coordinates. `JournalPreferences` separately remembers the last saved activity type. A new draft uses that type, its default label, and today, leaving distance/duration as the main inputs. Optional details are a rendering choice, not a persistence rule.

## Storage ports and mutation rules

Read, observe, find, insert/delete, update, and atomic import use narrow domain interfaces. Insert must not replace another ID. Update reports a missing ID rather than upserting. Mutations lock conflicting commands and run on an injected IO dispatcher. Failed saves keep the draft. Observation failures retain the current list and offer retry.

Delete removes the selected record and retains one undo candidate in memory. Undo uses ordinary insert semantics with the original ID, so a conflicting ID fails rather than overwriting. Snackbar dismissal, a later deletion/notice, or process death can end the undo opportunity. Undo is not a durable tombstone or backup.

## Backups and platform adapters

`JournalBackups` depends on reader/importer/codec ports. `JsonBackupCodec` lives in a framework-free adapter package and uses strict streaming JSON. `WalkImporter` is implemented through Room's transactional DAO method with insert-ignore conflict handling. The platform-neutral `BackupSource` and `BackupDestination` ports separate chosen-file access from parsing and merge policy. The app adapter bounds input and closes URI streams; no URI enters the domain.

## Maps and composition

Coordinates remain validated domain values. Map SDK objects stay in the maps package. The map picker confirms a coordinate into the draft; the activity Save action writes it. Provider rendering/network lifecycle remains separate from activity rules.

`TrailContainer` wires Room, use cases, JSON backup codec, and preferences. Domain/presentation have no Android or framework adapter dependencies. The original synchronous `JournalActions` remains for compatibility with the first sample; the new UI uses the focused use cases and observable port.


## Duration and health boundaries

`ActivityDuration` converts picker parts and formats durations while the model/database/backup keep whole minutes. The Compose picker commits one canonical value only after confirmation, so cancellation does not alter a draft.

Health SDK types are isolated in the `health` adapter package. Domain ports separately expose access status, reading, and sharing. `JournalHealth` handles identity, conversion, selection, and start/end rules. `HealthController` coordinates a separate health state flow and rechecks read/share permission, including import confirmation after a preview. The journal controller does not gain health-provider concerns. Health import reuses the atomic missing-ID importer and updates the ordinary Room observation flow.


## Single-module source organisation — 0.5.1

Only `app` remains in settings.gradle.kts. The namespace is `dev.michelle.trails`, with root UI/platform wiring and domain/presentation/data/backup/maps/health subpackages under app/src/main/java/dev/michelle/trails. External dependencies and KSP configuration are consolidated in app/build.gradle.kts. Unit and device tests are also app source sets.

Package boundaries retain the existing class responsibilities and narrow ports. Because Gradle no longer enforces separate library dependencies, scripts/check_architecture.py rejects Android/framework/adapter imports from domain and presentation. Their source groups are also compiled independently during local verification. The application ID remains singular (`dev.michelle.trail`) and the database filename/schema are unchanged. Component names are explicit in the manifest so namespace/application-ID differences do not misresolve activities.


## Durable health sync — 0.6.0

The production sync engine is `QueuedHealthSync`, behind `HealthSyncEngine`/`HealthActivitySharer`. It coordinates provider-neutral queue and writer/deleter ports. `HealthSyncController` handles lifecycle/observable state. The former preference-backed sync engine was replaced; its preference adapter is retained only for one-time timing/consent migration.

`RoomWalkRepository` uses `RoomHealthSyncQueue` to commit journal changes and the latest desired external operation atomically. The queue stores one revisioned job per owned activity. Acknowledgements and failures update only the same revision. Undo replaces a delete with a higher-version upsert; an in-flight stale response leaves the newest job pending. Provider work never runs inside the Room transaction.

`ActivityTiming` centralizes interval validation for form and sharing. The Android SDK adapter implements record upserts/deletes; a separate JobScheduler adapter supplies persistent background execution. Screens display immutable per-activity status and dispatch events. Provider failures do not turn committed journal saves into failed-save retries. Backup format 2 carries timing and accepts legacy format 1; external consent/jobs are intentionally excluded.

## Backup monitoring and accessibility — 0.7.0

BackupMonitor owns the receipt/reminder policy behind BackupStatusStore. JournalBackups prepares an immutable export snapshot; JournalController coordinates document writing before recording its receipt and prevents stale backup-status updates. SavedBackupStatusStore is the Android preferences adapter. BackupRecoveryCard renders state and dispatches events; it does not inspect document providers or calculate content freshness.

TrailTheme centralizes light/dark palettes for the main and privacy activities. UI-only changes add wrapping actions, scrollable dialogs, headings, contextual action descriptions, labelled compound toggles, accessible errors, keyboard navigation and overflow-based note expansion. These presentation concerns do not enter the domain or database layer. Room schema 4 and backup format 2 are unchanged.

## Read-only support inspection — 0.8.0

HealthSyncEngine exposes inspect separately from synchronize/retry. QueuedHealthSync projects a HealthSyncReport and latest-operation history without performing provider writes or acknowledgements. History projection, filtering and manual-retry deadlines are platform-neutral domain policies. The health panel and share-result dismissal now request inspection rather than resetting retry delays.

SupportDiagnostics reads an AppEnvironmentReader and the inspection port, then builds an explicit allowlist of operational flags. It never serializes queue/journal models or raw exception text. AndroidAppEnvironmentReader queries only this application's package version and the Android API level. SupportController coordinates preview and saving through the existing opaque text-document destination; the exact reviewed string is written on IO. Screens do not read preferences, decode records or call provider APIs. No database/backup schema change is needed.
