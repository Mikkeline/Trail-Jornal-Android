# Manual device checklist — 0.5.0

These checks have not been executed in the authoring environment. Record device, Android version, build, and actual outcome.

1. Fresh install: open Home and verify it shows progress/recent activities rather than the full form. Check bottom navigation and empty states.
2. Log an activity with distance/duration and the default date/type/name. Save and return to Home. Verify totals and the recent list update.
3. Save each activity type. Check a new draft remembers the last successfully saved type and uses today. Open optional details and save venue, effort, notes, and a pin.
4. Edit a record without changing its ID or adding a duplicate. Preserve all metadata and exact metre precision. Reopen after force-stop and verify persistence.
5. Reject blank/zero/negative/over-precision distances, fractional/invalid duration, blank title, invalid dates, and future completed dates. Errors should appear beside fields and open hidden details when necessary.
6. Pick dates in UTC+8 and UTC-8. Stored calendar dates must match selection.
7. Change a draft and try Home, History, Back, Clear form, and Cancel editing. Keep editing must preserve the draft. Confirmed discard must not write it. A clean form should leave without a prompt.
8. Rotate while editing and with the keyboard/validation open. Background and restore a system-killed task without force-stop or clearing data; verify draft, baseline, edit ID, metadata, and zero-valued coordinates.
9. Tap Save repeatedly during a write. Only one record should be submitted. Simulate failure where possible; keep input for retry and clear the busy indicator.
10. Search mixed-case names, venues, and notes. Combine type and date-period filters, check Monday/month boundaries and month headers, and verify the no-results state.
11. Delete from a card, tap Undo, and verify the same record ID and metadata return. Dismiss/expire the snackbar and confirm undo is session-only. Check conflict/failure handling and Retry undo.
12. Back up through the system picker to local storage and an available provider. Inspect JSON fields and pins. Cancel the picker and test provider/write errors; never report false success.
13. Restore into an empty journal and one with matching IDs that have newer edits. Preview must not write; Cancel must leave it untouched; Confirm must add only missing IDs. Repeating import must create no duplicates.
14. Reject malformed/oversized files, duplicate IDs/fields, unknown versions, invalid units/dates/coordinates, numeric-string substitutions, and truncated/invalid UTF-8 input. Failures must leave the journal intact.
15. Run `BackupImportTest`, including its trigger-induced failure. Verify Room rolls back earlier inserts and existing entries remain unchanged.
16. Pick a map location by panning/tapping or entering negative and zero coordinates. Cancel a different choice and retain the original draft pin. Save, edit, remove, reopen, and view in read-only mode.
17. Start offline, use manual coordinates, reconnect, and reload the map. Check attribution, fonts, GPU behavior, repeated open/close, rotation, and background/resume. No GPS permission prompt should appear.
18. Upgrade actual version-1 and version-2 installations using the same application ID/signing key without uninstalling. All original fields must remain; absent legacy pins stay null. Also update version 0.3.0 without a database schema change.
19. Run all data/app instrumentation tests. Inspect and retain generated Room schemas. The desktop SQL checks do not replace Room validation.
20. Check phones/tablets, portrait/landscape, large font, TalkBack, touch targets, keyboard scrolling, map/file dialogs, and disabled busy-state controls.


21. Choose duration presets and custom 1 h 30 min; test blank components, zero, minute 60, 168 h, cancel, rotation, large font, and screen readers. Existing saved/backup durations must be unchanged.
22. Run DurationInputTest on device. Check list and Home formatting for minutes, whole hours, and mixed durations.
23. On unsupported devices, keep the journal usable and show Health Connect unavailable. On supported older Android test provider installation/update; on Android 14+ test built-in settings.
24. Grant only reading, only sharing, both, neither, partial permissions, and revocation in Settings. Check rationale links on supported older Android and Android 14+.
25. Populate a test Health Connect source with walking/hiking and distance records. Preview without writing, select subsets, cancel, confirm, re-import, and verify preserved IDs/local edits. Test unsupported sessions, missing distance, future sessions, zero/partial seconds, pagination, and the 100-session limit.
26. Check source labels and cross-provider duplicates. Own shared workouts must not be imported. Imported source workouts must not be offered for re-sharing.
27. Share a manual activity only after choosing its actual start time. Verify session/distance values, manual-entry metadata, source ownership, stable client IDs, increasing version, explicit resharing, future-end rejection, and DST gaps/overlaps.
28. With automatic updates off, edit/delete a shared record and verify no automatic write/removal is claimed. With Update shared activities on, edit its date/title/type/distance/duration and verify the same Health Connect records update. Deletion still leaves the external copy.
29. Test permission denial/revocation mid-preview and before confirmation, provider failures, app background/rotation, unavailable Play/browser/settings handlers, and device-profile restrictions.
30. Test a real watch companion that actually writes eligible Health Connect records. Do not claim universal watch compatibility, daily-step sync, Apple Health support, or verified native integration from the JVM tests.

## Automatic Health Connect updates — 0.5.2

- Verify the switch defaults off, can be turned off after permission revocation, and requires write permission to enable.
- Share once with a real start time; enable updates. Change distance/duration and check Health Connect record IDs remain unchanged while values update.
- Change the activity date and the phone time zone; verify the saved original time zone and chosen local start time determine the new interval.
- Force-stop and reopen after an edit/provider failure. Verify pending work retries without duplicate records, including when the last write succeeded before process death.
- Revoke permissions, edit locally, then return: journal data stays saved and the health panel reports a paused/pending update. Regrant permission and return to retry.
- Edit notes/map pin/effort only; verify no extra Health Connect write.
- Verify imported workouts are excluded and new/older unregistered activities offer one-time start-time setup.
- Turn off automatic updates, edit a linked entry, reopen: no write. Turn it back on: latest values are sent.
- Delete a linked entry and verify the UI explains the external copy remains. Undo/recreate restoration must not cause duplicate exports.

## Release 0.6.0 — device checks still required

1. Upgrade an installed schema-3 journal without uninstalling. Verify existing entries/pins/notes survive, known 0.5.2 timing/consent migrates once, and unknown start times remain blank.
2. Enter a completed activity's start time; enable automatic sharing and save. Check exercise/distance in Health Connect and then Samsung Health. Confirm card status/last success and stable client IDs after editing distance/duration/date/time.
3. Reject future finishes, invalid times and DST gaps/overlaps. Change the phone time zone and verify previously selected zone is retained through editing and process recreation.
4. Revoke permission, save/edit locally and verify Needs permission. Regrant and return; check one logical copy with current values. Test per-entry Retry without enabling automatic sharing globally.
5. Simulate provider failure; confirm delayed retries, bounded backoff, persisted pending jobs after process death, and background execution/reboot. Force-stop must not be described as guaranteeing background execution.
6. Delete a synced record; Undo during the grace period and after deletion has run. Verify the latest desired state wins if an edit/Undo occurs while provider IPC is in flight. Only Trail Journal-owned exercise/distance records may be removed.
7. Restore v1 and v2 JSON backups, including optional timing and Unicode notes. Existing IDs stay unchanged. Imported health workouts must not feed back into exports.
8. Execute HealthSyncStorageTest, the updated 1→4/2→4/3→4 migration tests, and saved-state/Compose instrumentation. Verify rollback if a queue write fails after a journal mutation.

Earlier manual-only/deletion-retention descriptions above refer to older releases and are superseded by these checks.

## Release 0.7.0 — backup and accessibility

- Export, cancel the document picker, reject a write, and simulate document-provider failure. Last recorded backup must change only after a completed write.
- Edit notes/pins/start time or delete a row after export: the backup card must show unbacked changes, even if activity counts are unchanged. Reordering history must not affect freshness.
- Change the journal during export: the receipt must describe the exported snapshot and keep newer data marked as unbacked.
- Turn reminders off/on, snooze seven days and reopen. An unchanged older backup must not trigger a reminder. No notification permission or scheduled backup should appear.
- Open Recovery guide, restore a v1/v2 file, and confirm it explains notes/pins, reinstall risk and independent Health Connect permissions/consent.
- Follow ACCESSIBILITY.md for TalkBack, 200% font, narrow/landscape layouts, keyboard flow, dark mode and error/status announcements.

## Release 0.8.0 — history, support and release target

- Open Sync history from Home and the health panel. Confirm pending updates, missing-time/permission states, waiting deletions and accepted removals are visible with useful actions. Deleted titles must not be copied into a new history store.
- Refresh status and prepare diagnostics while a job has a future retry deadline: neither action may reset that deadline, acknowledge the job or start a provider write.
- Retry a failed removal explicitly: failure backoff can be bypassed, while the initial pending deletion's Undo grace must remain intact.
- Preview diagnostics containing operational states from real entries. Verify the saved text is exactly the preview and contains no activity details, IDs, locations or raw errors. Cancel the document picker and simulate write failure; neither action should report a saved file or alter the backup receipt.
- Run SyncSupportUiTest, release lint/build and the API-36 instrumentation workflow. Verify Android 16 insets/back handling, background-job behavior and native map loading, including a 16-KB environment.


## UI redesign — 0.9.0

1. On a fresh install, verify first-use guidance and Log activity. After saving, check real weekly/all-time totals and recent entries. Never show sample preview data in the installed app.
2. Switch Home/Journal, use Log activity, and edit via a card's More options menu. Verify Details, long-note expansion, Map, Share, Delete and Undo.
3. Combine search, activity and date filters, clear search, reset all filters and recover from no matches without changing stored entries.
4. Enter a long note, open the keyboard and scroll. Save/Cancel must remain reachable without covering the focused field on small phones, landscape and 200% font. Check error scrolling and draft discard guards.
5. From an unsaved form open Journal tools, health settings, sync history and backup recovery, then return. Retain the draft. Export/import cancellation must keep entries intact.
6. Verify the launcher icon, Android 13+ themed icon, cold launch and system bars in both light/dark mode. Review native date/time pickers, contrast, TalkBack order and touch targets.
7. Run JournalRedesignTest and the existing instrumentation suite. Render all debug previews, then repeat critical flows on a real phone; compilation alone does not verify these interactions.


## Secondary layouts — 0.9.1

1. Open Health apps and verify Connection, Automatic sharing and Import workouts sections. Confirm compatibility help can expand/collapse without changing permissions or sharing consent.
2. Preview a long workout list. Select/deselect and clear/select all, scroll to the last item and use the fixed Import button. Back cancels only the preview and returns to health settings; no import occurs until confirmed. During import, submission and Back are disabled. Check error/retry and permission revocation.
3. In Sync history, compare waiting/attention/completed counts to actual operations. Refresh must inspect status without retrying writes. Open its options menu for health access or diagnostics. Verify all filters and per-entry Retry/Edit still work.
4. Pick a map pin, pan/zoom and confirm with the fixed action. Expand manual coordinates, test negative/zero values and invalid input, hide/reopen, and use keyboard/landscape/200% text. Force map loading failure: coordinates auto-expand and remain usable offline. They must also collapse again when requested.
5. Back up now, cancel the file picker, restore with preview, and read the full recovery guide. Canceling must not claim a successful backup or change the journal.
6. Open diagnostics, scroll/select text and Save using the fixed action. Cancel the picker and retry after an error; verify the exported content still exactly matches the preview.
7. Render new debug previews, then run HealthLayoutTest, SyncSupportUiTest, BackupAccessibilityTest and the complete device suite. Check system Back, display cutouts, TalkBack focus, large text and keyboard insets on each full-screen dialog.
