# Engineering decisions

## 001 — Domain ports and manual injection

Keep domain and presentation platform-neutral. Constructor injection and small ports make storage, observation, update semantics, and draft restoration independently testable. Concrete classes for pure operations do not each need an interface. Trade-off: the application explicitly wires dependencies.

## 002 — Compose and MVVM

Replace the first sample's platform Views and serial executor with Compose rendering, a lifecycle-owned ViewModel, and coroutines. Extract a plain Kotlin controller to test presentation behavior without Android. Trade-off: a larger dependency/toolchain surface and a required Android build before claiming a verified app. Dependency pins are explicit and are not advertised as the latest releases.

## 003 — Exact distance parsing

Accept decimal kilometres with up to three places, convert through BigDecimal to integer metres, and reject additional precision rather than round silently. Use a UTC date-picker conversion to avoid moving dates across time zones. Future completed activities are rejected by an injected-date validator.

## 004 — Room migration, not database replacement

Keep the existing database filename and installation identity. Rebuild the legacy table inside the upgrade transaction to align the primary-key constraint and add activity metadata. No destructive fallback. Legacy types default to Outdoor walking and remain editable. Corrupt null IDs fail migration rather than being silently removed.

## 005 — Draft persistence and mutation semantics

Use SavedStateHandle for small editor state and Room for records. Do not clear the draft until the write succeeds. Reject duplicate commands while a mutation is in flight. Editing uses update rather than upsert to avoid recreating entries deleted during an edit. This does not provide multi-client conflict detection or exactly-once writes across process death.

## 006 — Single location pin with an isolated map adapter

Use MapLibre Native with a configurable public OpenFreeMap style for an initial online map without API-key setup. Pin selection is explicit; no GPS permission, geolocation service, route recording, or reverse-geocoding dependency is introduced. Keep the rendering/lifecycle implementation in a maps module and domain coordinates independent of it. The SDK is pinned to the public example's 11.8.0 API baseline; it is not claimed to be the newest release. Validate native rendering and upgrade compatibility before a production release.

A separate nullable coordinate pair extends the Room schema without assigning imaginary pins to old entries. Manual coordinate entry is available when map resources cannot load. Provider availability and map coverage are external dependencies.


## 007 — Simple destination state and guarded drafts

Use Home, History, and Editor destinations without introducing a navigation framework for three screens. Keep the queued navigation/discard decision in the testable controller, not in unrelated click handlers. Store a draft baseline so restoration can retain the dirty-state decision. Default the activity label/date/type and collapse optional input to reduce logging effort.

## 008 — Undo instead of repeated delete prompts

Delete immediately and offer one session-only undo action. Restore through insert with the original ID; fail on conflicts. Trade-off: the undo candidate is not durable across process death and does not replace backups.

## 009 — Versioned JSON with preview and atomic merge

Keep JSON handling in a JVM adapter behind a codec port. Validate strict, bounded input and all domain values before showing a preview. Use a Room transaction to insert missing IDs and keep existing entries. Trade-off: this is a merge, not full replacement or cloud conflict resolution. Limits are 10 MiB and 10,000 entries. Use the Android system document picker rather than broad storage permission.


## 010 — Friendly duration entry without a storage migration

Use clock-style hours/minutes and quick presets, but keep canonical whole-minute values in domain, saved state, Room and backups. The picker commits only after confirmation; display formatting is independent of parsing.

## 011 — Explicit workout exchange through platform ports

Use stable Health Connect 1.1.0 on Android, with separate read/share permission actions and bounded, selected import. Do not imply direct Bluetooth-watch pairing or Android access to Apple Health. Preserve external identity in deterministic journal IDs, skip missing distance rather than inventing it, state elapsed-time rounding, and block feedback copies. Sharing requires an actual user-selected start time and uses stable client IDs/versions. No background sync or automatic cross-store deletion is introduced.
