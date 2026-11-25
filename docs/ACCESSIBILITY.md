# Accessibility and recovery checks — 0.7.0

## Implemented

- System light/dark theme shared by the journal and health privacy screen.
- Explicit theme primary/secondary/surface foreground-background pairs exceed 4.5:1 in a numeric contrast check. This does not certify every rendered state or native map/control.
- Screen/section/activity headings expose heading semantics. Repeated Edit/Delete/Share/Retry/map actions include the activity title/date in their accessibility descriptions.
- Form errors expose semantics; global validation/action messages use polite live announcements. Duration errors are associated with the duration control.
- Backup and health setting rows are labelled compound switches. Workout import rows combine their checkbox and descriptive text into one toggle target.
- Action rows wrap where needed. Duration hour/minute inputs stack, dialogs/privacy text scroll, map controls/coordinates remain reachable below a fixed-height preview, and optional note expansion depends on actual text overflow.
- Numeric/text fields provide appropriate keyboard types and Next/Done actions; multiline notes keep their newline input.
- Recovery guide remains accessible from Home. Reminders stay inside the app and can be disabled or snoozed.

## Before claiming accessibility support on release

1. Use TalkBack with headings navigation, every activity action, both reminder/health switches, import selection, errors and recovery/permission dialogs. Check that each action has one useful announcement and no unlabeled controls.
2. Check font sizes up to 200%, larger display size, 320-dp width, landscape, light/dark themes and keyboard-visible forms. Every action must remain reachable without clipped labels or hidden buttons.
3. Confirm focus order and keyboard Next between inputs. Multiline notes must still accept line breaks; duration Done must dismiss its keyboard.
4. Test duration and recovery dialogs with large text; scroll to their final content and confirm/cancel buttons. Check the map's coordinate alternative when gestures are difficult or the online map fails.
5. Run BackupAccessibilityTest and the retained Compose instrumentation. Source compilation alone does not execute these accessibility checks.
6. Verify reminder announcements do not repeat unnecessarily and that statuses/errors convey meaning without relying on color alone.


## UI redesign checks — 0.9.0

The shared app bar reduces decoration at large font sizes; card actions have contextual overflow-menu labels, activity sync states include text, and form Save/Cancel remain outside the scrolling fields. Home and History use semantic headings; filters expose complete activity labels. Light/dark palettes use explicit contrasting text roles. Debug previews include 200% text.

Verify focus order after closing Tools/menus, Save visibility above the keyboard, long translations/text wrapping, error announcements, and the disabled Undo action during saving on a real device. Numeric palette checks and source compilation do not replace TalkBack or rendered accessibility review.


## Secondary layouts — 0.9.1

Health, workout import, sync history, map, recovery and diagnostics share a fixed heading/back control and safe drawing insets. Primary import/map/report actions are separate from scrolling content. Expandable Help exposes Expanded/Collapsed state, and workout rows expose one combined checkbox action. Sync totals and statuses use text, including for errors. Manual coordinate errors remain exposed through field semantics. Run the secondary-layout manual checks on device before treating these changes as visually or accessibility verified.
