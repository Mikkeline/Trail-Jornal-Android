# Health Connect — 0.6.0

## Sharing and automatic updates

Trail Journal exchanges ExerciseSession and Distance records through Android Health Connect. Samsung Health and other receiving apps need their own read support and permission. An accepted write to Health Connect does not prove another app has imported it.

Enter a real **Start time** in Add/Edit activity. The time zone is retained with the activity, including when the phone later changes zones. Times that are invalid, ambiguous during DST, or imply a future finish receive a field error. Start time is optional for local-only journaling; older rows remain valid with no invented time.

Enable **Health apps & smartwatches → Automatically share & update**. Saving a manual activity with timing queues its first export. Changes to title, date, type, distance, duration or timing queue an upsert using this app's stable client IDs and a monotonically increasing version. Notes, effort and map pins are not shared and do not cause extra writes. Imported workouts are excluded.

Explicit **Share to Health Connect** still works with automation off and saves the chosen timing into the activity. Cards show sync state, last accepted-write time, failures and Retry. “Synced” means Health Connect accepted the records. Unknown times show Needs start time; permission revocation leaves pending work intact.

## Durable storage and retries

Room schema 4 stores nullable startTime/zoneId fields, health_sync jobs and health_sync_settings. Journal changes and jobs are committed in the same transaction. A job is the latest desired operation for an activity, with revision, provider record version, automatic/explicit intent, retry deadline, attempts, last successful sync and error. Acknowledgements use compare-and-set on revision; a newer edit or Undo cannot be cleared by an old response.

Existing 0.5.2 timing/consent preferences are imported once, transactionally. Previously issued record-version values are respected even if the system clock moves backwards. Stable client IDs prevent intentional duplicate copies on retry. New JSON backups include timing but exclude sync consent/jobs; v1 backups remain readable.

Transient provider errors use exponential backoff from 30 seconds to six hours. Invalid timing and permission errors require correction/regrant or explicit retry. A persistent Android JobScheduler adapter runs deferred work without another runtime dependency. Android controls execution timing and may defer it for power limits; force-stopping the app requires reopening before work resumes. No background health-read permission, foreground service or app-owned server is used. Foreground journal changes, resume and explicit retry also drain eligible jobs.

## Delete and Undo

Deleting an activity with an owned export job atomically replaces its job with DELETE, due after a 30-second grace period. Only this app's stable exercise/distance client IDs are removed. Undo restores the journal row and replaces DELETE with UPSERT. If deletion already finished or was in flight, the restoring job repairs the external copy. Undo remains the journal's session-only action; it is not a durable trash/recovery feature. Other apps control their own copies of imported data.

## Import

Preview reads up to 100 sessions from the last 30 days. Walking/hiking sessions with positive distance from the same source are eligible. Nothing imports until selection/confirmation. Stable source identities prevent repeat import over existing local edits. Own exports are excluded to avoid loops. Missing distance, unsupported types and invalid/future intervals are skipped. Elapsed time includes pauses and is rounded up to whole minutes; source/timing notes preserve provenance. Daily steps, heart rate, sleep, routes and raw Bluetooth-watch pairing are not implemented.

## Privacy and release verification

Only exercise and distance read/write permissions are requested. Start-time metadata, journal contents and jobs stay on the device. Backups are explicit plain-text files and can contain notes/pins. Revoking permission pauses transfer without removing local data. The rationale screen explains automatic sharing, background retries and deletion. Apple Health requires a separate iOS/HealthKit companion, which is not included.

Full release validation still needs Room/KSP generation, an in-place v3→v4 upgrade, actual SDK upserts/deletion retries, background scheduling/reboot/force-stop, permission revocation, and Samsung Health refresh behavior on devices. See MANUAL_TESTS.md and VALIDATION.md. Google Play publication also needs the public privacy policy and appropriate Health/Data safety declarations.

Primary documentation:

- https://developer.android.com/health-and-fitness/health-connect/sync-data
- https://developer.android.com/health-and-fitness/health-connect/write-data
- https://developer.android.com/reference/android/app/job/JobService
- https://developer.android.com/health-and-fitness/health-connect/publish
