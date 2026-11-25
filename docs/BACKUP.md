# Backup and restore

## File access

The Android adapter uses CreateDocument/OpenDocument contracts and accesses only the URI chosen by the user. It adds no broad storage permission and uploads nothing automatically. All document IO and codec work run on the injected IO dispatcher. Input streams are read with a 10 MiB limit, UTF-8 is decoded without silently replacing malformed bytes, and streams are closed.

A failed export may leave a partial/empty destination file; only a completed write and close produce success. Select a new destination and retry. The journal database is not changed by export. Backup files include all activity fields, notes, and location pins in plain JSON.

## Format

The root has `format: "trail-journal"`, `version: 1`, and an `entries` array. Format version is independent of app/database version. Every entry has id, title, date, distanceMeters, durationMinutes, notes, type, location, effort, and point. Effort and point may be null. A point has numeric latitude and longitude; zero is valid.

The streaming Gson adapter uses strict JSON, explicit fields and primitive types, bounded entry count, and fixed nesting. It rejects unknown/duplicate/missing fields, unsupported versions, trailing content, numeric strings where whole numbers are expected, invalid domain values, and duplicate activity IDs. It does not instantiate arbitrary classes from file contents. Limits are 10 MiB UTF-8 and 10,000 entries.

## Preview and merge

Preview validates the entire file without writing records. It then compares IDs with the current journal and presents counts. Cancel makes no changes. Confirmation runs a Room transaction that inserts only missing IDs. Existing entries, including later edits, are never replaced. Counts are recomputed by the actual merge, so the final report can differ if data changed after preview. A failure rolls back inserted rows rather than leaving a partial restore.

The import plan stays in the ViewModel's memory during the preview; it is not written to SavedStateHandle. Process death before confirmation means selecting the file again. Repeating a completed import is safe because IDs are preserved. Restoring is a merge, not a full database replacement or a synchronisation system.

## Test evidence

The strict codec and backup use cases run in the plain JVM suite. Controller tests cover preview/cancel/confirm/retry/export. Android tests have been added for duplicate-ID preservation and transaction rollback after an injected SQLite trigger failure, but have not run in this environment. System picker, provider IO, storage errors, cancellation, and device Room behavior still need the manual checks.

## Timing fields — format 2

New backups include nullable startTime and zoneId as a pair. Version-1 backups are still accepted with no invented start time. Restore does not import sync consent, job revisions, pending deletion intents or last-synced claims from another installation. If automatic sharing was already enabled locally, restored manual activities with valid timing are queued under that existing consent; imported health-source activities remain excluded.

## Backup status and reminders — 0.7.0

A PreparedBackup captures encoded contents, activity count and a deterministic content fingerprint from the same snapshot. Only after the document write closes successfully does BackupMonitor record the export time/fingerprint/count. A failed write cannot claim a successful backup. If recording local status fails after the file was written, the app reports the completed file with a status warning rather than inviting a duplicate failed-save retry.

The fingerprint includes all journal fields, including private notes, pins and time-zone metadata, and ignores row display order. It is compared with current contents on journal observations and app resume. Concurrent edits during an export remain marked as unbacked changes. Stale asynchronous status refreshes cannot overwrite a newer refresh or a different displayed journal snapshot.

Reminders are shown in Home only: no notification permission, alarm or background backup is introduced. A nonempty journal without an export gets an initial reminder. After a recorded export, a reminder needs both changed content and at least seven elapsed days. Unchanged old exports do not cause repeated reminders. Snooze and the reminder switch persist locally. Receipt/reminder settings are not included in journal JSON backups.

Last backup created is a record of a completed export, not proof that a document provider still retains the file or has uploaded it to the cloud. A new installation shows no recorded export even after restoring a chosen file. Recovery guide explains choosing a file outside app storage, previewing/merging on restore, granting health permissions again, and the fact that Health Connect does not store notes/map pins. Backups remain plain-text files chosen by the user.
