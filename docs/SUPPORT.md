# Sync history and diagnostics — 0.8.0

Open **Home → Sync history & diagnostics**, or the matching button in Health apps & smartwatches.

## Sync history

The screen projects the latest operation stored for each owned activity, including pending deletions and accepted removals after its journal row is gone. It is not a complete event-by-event audit log. Existing deletion jobs use a short derived reference when the original title has been removed; the app does not retain an extra copy of deleted titles or notes for this screen.

Filters show All, Pending & attention, or Completed. Rows show share/update versus removal, the last successful provider-operation time when known, failure count, relevant retry/grace deadline, actionable guidance and Retry/Edit controls. A completed status means the provider accepted the operation; Samsung Health controls its own refresh and retained copies.

Refresh status uses read-only inspection. It does not acknowledge jobs, reset backoff or send records to Health Connect. Automatic jobs can still run independently under the user's existing sync consent. Retry is explicit: it can retry one activity with automation off. A pending deletion's Undo grace is preserved; a failed deletion has already passed that grace, so explicit Retry can bypass its failure backoff.

## Diagnostic file

**Preview diagnostics** collects an on-device snapshot, then shows the exact text. **Save report** opens Android's document picker. Canceling the picker leaves the preview intact. Saving uses the reviewed text rather than silently recollecting a different report. The app does not send the file to support automatically, and this action does not mark the journal as backed up.

The formatter uses an explicit allowlist:

- Report format and generation time in UTC.
- App version/code and Android API level.
- Health Connect availability and exercise/distance read/write permission flags.
- Automatic-sharing setting, presence of pending work, and operational flags for permission, timing, retry, attention and Undo grace.

It excludes journal/activity IDs, titles, dates, distances, durations, notes, locations, coordinates, device identifiers, raw provider errors, file paths, stack traces, counts of activities and operation timestamps. Inspection failure is represented as unavailable/unknown without exception details. Tests inject private sentinel data into titles/IDs/errors to prevent accidental serialization regressions.

The full raw error can still be viewed locally in sync history when available. Export failures retain the preview and show a generic recovery message. Report preparation and document IO use separate platform-neutral ports and a dedicated presentation controller.
