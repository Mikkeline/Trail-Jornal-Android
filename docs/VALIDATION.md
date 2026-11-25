# Validation record — 0.5.0

Date: 2026-11-19.

| Check | Result |
| --- | --- |
| Domain source-group JVM compilation | Passed with Kotlin 2.0.21 / JVM 17 / coroutines 1.9.0 |
| Presentation source-group JVM compilation | Passed |
| Backup codec source-group JVM compilation | Passed against Gson 2.11.0 |
| Domain JUnit tests | 50 passed |
| Presentation/controller JUnit tests | 27 passed |
| Strict JSON/backup JUnit tests | 10 passed |
| Desktop SQLite migration checks | 6 passed |
| Android ViewModel, saved-state, preference and document adapters | Compiled against installed Android API 35 and cached Lifecycle 2.6.2; this is not the pinned 2.9.0 build |
| Kotlin source/build syntax parsing | Passed |
| Native Health Connect adapter, permission contract and runtime behavior | Source added; compilation/device verification pending |
| Compose duration picker instrumentation | Added; not run |
| Full Gradle/Compose/Room/KSP/maps/health build | Not verified; environment blocks Gradle's local socket and dependency downloads |
| Device/instrumentation tests and native maps | Added/retained; not run |
| System document picker and provider IO | Not run on device |
| GitHub Actions | Updated; not run on GitHub |

87 JUnit tests were compiled/executed directly against real Kotlin/coroutines/Gson/JUnit libraries. Syntax parsing cannot validate framework APIs, generated Room implementations, resources, dexing, or packaging. No successful APK build or production-readiness claim is made.

The suite covers existing activity/pin rules, history date/type/text combinations, dirty-state restoration and navigation, save failure/retry, undo, duplicate commands, backup round trips and strict rejection, preview/cancel/confirm/retry, and keeping existing IDs. The Android import test injects a SQLite trigger failure to check transactional rollback; this still needs actual Room/device execution. The desktop migration checks cover schema upgrades, not generated Room transaction code.

Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
python3 scripts/check_legacy_migration.py
```

Complete `MANUAL_TESTS.md` before claiming device support or adding build badges.

New JVM coverage includes hours/minutes conversion, friendly formatting, health import rounding/identity/selection, explicit share times and DST/future rejection, denied/revoked permissions, and provider failure. The health SDK is not installed in the tool cache; these tests use domain-port substitutes and must not be described as a successful native Health Connect build or smartwatch/Apple Health verification.


## Single-module reorganisation — 0.5.1

All handwritten sources and tests were moved into app source sets and their packages renamed to dev.michelle.trails. The external dependencies, manifest permissions/package queries, KSP configuration, workflows and script paths were consolidated. Previously generated Room schema exports were preserved byte-for-byte under the new database-class folder. The installed application ID, database filename/schema and local SDK configuration were preserved.

After relocation, the platform-neutral source groups were independently compiled and all 87 JVM tests rerun. The six desktop migration checks and the new import-boundary check were also run. Full single-module Gradle/Android compilation and device execution remain unverified in this tool environment. PackageMoveDatabaseTest checks opening an existing version-3 database with its original Room identity hash; it has been added but not run.


## Health transfer diagnostics — 2026-11-19

All 90 JVM tests pass (50 domain, 30 presentation, 10 backup). New checks cover permission approval without an implicit write, a successful share receipt surviving settings/resume until dismissed, and failed writes preserving the dialog without claiming delivery. The architecture check passes and all 59 Kotlin source/build files parse without syntax errors.

The three health adapter files now compile directly against the real cached Health Connect 1.1.0 SDK, Activity 1.10.1 and Android API 35. The writer checks that the provider returns two nonblank record IDs; this confirms provider acceptance, not that a receiving app has imported them. The UI exposes a persistent share result and the official Health Connect data-management intent.

Full Gradle compilation was attempted but blocked by permission to create the wrapper cache lock. Compose integration, APK installation, actual device data, permission flows, and receiving-app interoperability remain unverified. Earlier validation records above describe their respective checkpoints; this entry supersedes their SDK-cache and test-count statements.


## Automatic Health Connect updates — 0.5.2

104 JVM tests pass: 61 domain, 33 presentation, and 10 backup tests. New coverage checks default-off behavior, mandatory real start times, persisted original time zone, unchanged/private-field suppression, stable journal identity on edits, failed writes and retries after service recreation, initial-write failure, permission revocation, disabling updates, invalid future dates, imported/deleted-entry exclusion, cancellation, active journal observation, and retry on resume. The architecture check passes for 13 domain/presentation files; all 64 Kotlin source/build files parse without syntax errors.

All handwritten Android production sources compile directly with Kotlin 2.0.21, the Compose compiler plugin, real cached AndroidX/Room/MapLibre/Health Connect dependencies and Android API 36. This includes the Compose health settings, lifecycle wiring, persistent preferences adapter, dependency injection and Health Connect APIs. This is a compiler check, not a Gradle build: KSP generation, manifest/resource processing, dexing, packaging and physical-device automatic synchronization have not been executed for this change.

No Room schema or journal backup-format change was required. The installed application ID remains dev.michelle.trail; version code/name are 7/0.5.2. Pending changes are derived from the current journal plus persisted timing/signatures. Unlinked/new activities need one initial share; existing pre-0.5.2 exports need a one-time reshare. Deletion and scheduled background synchronization remain outside this release.


## Priorities 1–4 — version 0.6.0, 2026-11-25

Implemented optional actual start time/time zone in form, model, saved state, Room and backup format 2; per-activity sync state/last accepted write/Retry; atomic owned-record deletion intents with a 30-second Undo grace period; and a revisioned Room outbox with exponential retries and an Android JobScheduler adapter. The production preference-backed sync engine was replaced with QueuedHealthSync. Only its legacy timing/consent reader remains for upgrade migration.

| Check | Current result |
| --- | --- |
| JVM domain/presentation/backup tests | **112 passed** (65 domain, 34 presentation, 13 backup) |
| Desktop migration and sync-storage SQL | **12 passed** |
| Architecture boundary check | Passed for 15 domain/presentation files |
| Android production source compilation | Passed with real cached dependencies, Kotlin 2.0.21, Compose compiler and API 36 |
| Instrumentation-source API compilation | Passed, including the new Room outbox and sync UI tests; corrected two outdated JournalScreen test calls |
| Full Gradle test/APK build | Attempted; blocked starting the Gradle daemon by java.net.SocketException: Operation not permitted |
| Room/KSP code generation and schema-4 export | Pending full Gradle build |
| Physical-device schema upgrade, background job/reboot, Health Connect deletion/Undo and Samsung Health refresh | Pending device execution |

New JVM coverage exercises form interval validation, preserved time zones, old/new backup formats, queued first exports, permission states, failed writes/backoff and recreation, delete grace, restoration intent, cancellation and stale acknowledgements. Desktop SQL tests execute the migration/acknowledgement queries from production sources and check rollback and revision guards; they do not execute Room-generated transaction code.

Instrumentation coverage has been added for transaction rollback on queue failure, queue recreation, stale-delete completion after Undo, unchanged private fields, import rollback, one-time legacy timing/consent migration, scheduler failure after a successful commit, status Retry, and the start-time field. These tests compile but have not run on an emulator/device.

Application ID remains dev.michelle.trail, namespace remains dev.michelle.trails, database remains walks.db. Version is 8/0.6.0; schema is 4 with registered 1→2→3→4 migrations. No verified release APK or production-readiness claim is made. Earlier checkpoints above are historical and superseded by this entry.


## Priorities 5–6 — version 0.7.0, 2026-11-25

Implemented content-aware backup receipts, persistent in-app reminder controls/seven-day snooze, and a recovery guide. Successful export metadata refers to the exact encoded snapshot; failed document writes cannot claim a backup, and status-persistence failure after a completed write is reported separately. A regression test found and fixed stale backup freshness when the journal's display order differed from repository order during a concurrent export.

Accessibility/UI work includes shared system light/dark theme, contextual action labels, heading/error semantics, labelled compound switches and workout checkboxes, wrapping actions, scrollable privacy/recovery/share/duration/map content, stacked duration/coordinate inputs, keyboard Next/Done handling, and note expansion based on actual overflow rather than a fixed character-count heuristic. Multiline notes retain newline input.

| Check | Result |
| --- | --- |
| JVM tests | **124 passed**: 74 domain, 37 presentation, 13 backup |
| Architecture boundary check | Passed for 16 domain/presentation files |
| Handwritten Android production source compilation | Passed against real cached dependencies, API 36 and Compose compiler |
| Instrumentation-source API compilation | Passed, including BackupAccessibilityTest |
| Explicit primary/secondary/surface text-role contrast pairs | Numerically checked above 4.5:1 in both themes; lowest checked pair 6.45:1 |
| Room schema / backup JSON version | Remain 4 / 2; no migration added in 0.7.0 |
| Retained desktop SQL checks | 12 passed at the previous checkpoint; SQL/storage unchanged in this update |
| Full Gradle/KSP/APK build | Not executed successfully; the previously confirmed Gradle daemon socket restriction remains |
| TalkBack, actual 200% font/narrow-screen layouts, native map and document-provider behavior | Device execution pending |

New JVM coverage checks initial/old/unchanged backup reminders, changed private fields, deletion of all entries, ordering-independent fingerprints, snooze/recreation, disabling reminders, exported snapshot accuracy, file-write failure and receipt-write failure. The new Compose instrumentation covers labelled switches, recovery at large font/dark theme, contextual actions/error semantics, and expansion of shorter notes when large text overflows. Those device tests compile but have not run.

Application ID stays dev.michelle.trail, namespace dev.michelle.trails, database walks.db. Version code/name are 9/0.7.0. No notifications, scheduled backups, automatic cloud storage, or new health permissions were added. Backup receipts record completed exports, not ongoing availability or cloud durability of external files. See ACCESSIBILITY.md and MANUAL_TESTS.md before claiming release accessibility support.


## Sync history, support and release checks — 0.8.0, 2026-11-25

Implemented a latest-operation history projection for owned sync jobs, including pending/accepted removals after journal deletion. The screen exposes filters, actionable guidance, failure counts, known success/retry/grace times, Retry/Edit, health settings and a diagnostic preview. It retains no extra copy of deleted titles/notes and is not an event-by-event audit trail.

HealthSyncEngine now separates read-only inspect from synchronize/retry. Health-panel viewing, history refresh and diagnostic preparation use inspection without provider writes, acknowledgements or retry resets. Explicit failed-removal retry can bypass its failure delay, while the original pending removal grace is preserved.

SupportDiagnostics uses an allowlist of app/API version, provider permissions and operational flags. It excludes full models, activity identifiers/details, raw exceptions and operation timestamps. SupportController writes exactly the reviewed string only after a document destination is chosen. Export failure retains the preview and uses a generic error message. No remote support endpoint was added.

| Check | Result |
| --- | --- |
| JVM tests | **145 passed**: 90 domain, 42 presentation, 13 backup |
| Desktop migration/storage SQL | **12 passed** |
| Architecture boundary check | Passed for 19 domain/presentation files |
| Android app-source compilation | Passed with cached real dependencies, API 36 and Compose compiler |
| Instrumentation-source API compilation | Passed, including SyncSupportUiTest and new removal-retry storage coverage |
| Workflow YAML | Both files parsed with Ruby YAML |
| Pinned native SDK ELF alignment | MapLibre 11.8.0 arm64-v8a and x86_64 LOAD segments passed the new 16-KB checker |
| Full Gradle unit/lint/APK/bundle attempt | Blocked at daemon startup by java.net.SocketException: Operation not permitted |
| Actual release APK ZIP/AAB alignment, API-36 runtime, 16-KB device and live provider interoperability | Pending a successful build and device execution |
| GitHub workflow execution | Configured, not run remotely |

Compile/target SDK are now 36, following the reviewed current Play target requirement. CI installs matching platform/build tools, adds release lint/APK/bundle builds, storage/boundary checks and ELF/ZIP checks for the unsigned release APK, and uploads reports/schemas/unsigned artifacts. The manual instrumentation workflow now uses API 36. Native SDK ELF checks are not proof of final APK/AAB packaging or runtime compatibility.

New tests cover deletion visibility, all history filters, timing/permission/unavailable states, preserving grace versus bypassing failed-removal backoff, read-only inspection, exact reviewed exports, failed export recovery, allowlisted diagnostic fields, private-data sentinel exclusion and cancellation. Added Compose tests compile but have not run on a device.

Version code/name are 10/0.8.0. App ID remains dev.michelle.trail; Room schema 4 and JSON format 2 are unchanged. See RELEASE.md and SUPPORT.md. Full signing/publishing and a production-readiness claim remain outside these completed source checks.


## UI redesign — version 0.9.0, 2026-11-25

Version 11/0.9.0 adds a forest theme, focused Home dashboard, Home/Journal navigation, compact cards with overflow actions and expandable metadata, grouped editor with sticky Save/Cancel, filter reset, a Tools sheet, branded adaptive icon and launch resources. Debug-only previews cover light/dark, first use, editor, history and 200% text. Application ID, schema 4 and backup format 2 are unchanged.

- **146 JVM tests pass** (90 domain, 43 presentation, 13 backup), including clearing every history filter without changing stored activities.
- All Android production sources compile with Kotlin 2.0.21, the Compose compiler, API 36 and real cached dependencies. Debug preview and instrumentation sources compile separately against those app classes.
- Android resource compilation and linking pass using API-36 aapt2 and a temporary packaged manifest. This is a resource-only package, not an installable application build.
- Nine explicit foreground/background text-color role pairs per theme meet 4.5:1 in a numerical contrast calculation. This does not verify rendered accessibility or every UI state.
- New device tests cover opening health tools without navigating away from an unsaved form, and reaching Save after scrolling long optional details. Existing delete/optional-details tests were adjusted to the new controls. Device tests and previews have **not** been executed/rendered here.
- Full Gradle/KSP/APK, release lint and on-device visual/keyboard/TalkBack checks remain pending. A fresh Gradle attempt is blocked by `java.net.SocketException: Operation not permitted` while creating the daemon socket. No production-readiness certification is claimed.


## Secondary UI layouts — version 0.9.1, 2026-11-25

Version 12/0.9.1 adds a shared full-screen detail layout with fixed heading/back controls and optional fixed primary actions. Health settings are grouped into connection, automatic sharing and import; workout preview is a separate selection page using existing HealthController events. Map confirmation stays outside scrolling content, manual coordinates can collapse and automatically expand after map failure. Sync history summarizes waiting/attention/completed operations and moves diagnostics to its options menu. Backup actions have a clear hierarchy; recovery and diagnostics use full-screen layouts. Existing transaction, permission, import, retry and diagnostic privacy rules are retained.

- **146 JVM tests passed**; all 99 Kotlin source/build files parsed without syntax errors.
- All Android production sources compiled using Kotlin 2.0.21, Compose compiler, API 36 and real cached dependencies.
- Debug previews compiled, including health settings, import selection at standard/200% text, sync history, backups and diagnostics. Preview mode renders detail content without creating a real dialog window; sample data remains debug-only.
- All instrumentation sources compiled. New HealthLayoutTest coverage checks selection of the last item in a long list before explicit import, cancel without importing, disabled navigation/submission while busy and explicit diagnostics preview from the options menu. These tests were not executed on a device.
- Architecture boundary check passed for 19 domain/presentation files. All 12 desktop SQLite migration/sync-storage checks passed.
- No changes to application ID, database schema 4, backup format 2 or Health Connect record formats. GitHub description suggestions were added locally; no GitHub repository or settings were created/changed.

Full Gradle/KSP/APK generation, release lint, rendered preview/device review and instrumentation execution remain pending due to the previously recorded Gradle daemon socket restriction. This is source-level validation, not a verified production release or confirmation of receiving-app compatibility.
