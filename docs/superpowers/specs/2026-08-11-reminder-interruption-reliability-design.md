# Reminder Interruption Reliability Design

## Goal

Prevent a temporary system or third-party popup from being treated as a completed Focus reminder.  A visible reminder remains pending until the user makes an explicit choice; a reset clearly reports success but does not manufacture an immediate reminder.

## Confirmed behavior

- Tapping **Reset reminder quota** ends the current detection session, clears reminder marks inside the configured time window, and shows a short success message on Home.
- Reset does not show a reminder immediately.  The next entry into a configured target app starts a new session and observes the configured delay.
- Once a reminder is being presented, a temporary foreground-window change must not close that target-app session, dismiss the reminder, or spend another reminder allowance.
- A reminder is resolved only by an explicit reminder action, guardian shutdown, quota reset, or a genuine session-ending transition after the reminder has been resolved.
- No AI cache, usage history, or task data is cleared by a quota reset.

## Approach

Use the existing `ReminderPresentationRegistry` as a pending-reminder state rather than as an Activity visibility flag.  The registry becomes active when the reminder Activity is presented and remains active while an unrelated window temporarily covers it.  Detection services pass that pending state to `AppSessionCoordinator`, which preserves the open target session instead of cancelling its scheduler and dismissing the reminder.

An explicit response from `ReminderViewModel` clears the pending state before launching Focus, Home, a custom app, or scheduling a follow-up.  Existing explicit cancellation paths (`stopCurrentSession` and scheduler cancellation) continue to dismiss and clear the presentation.  The Activity lifecycle must not clear pending state merely because it receives `onStop`.

## Error handling and limits

- If Android destroys the reminder Activity, its destruction clears the registry so future detection can recover normally.
- The change does not attempt to override lock-screen, phone-call, payment, or Android permission safety behavior.
- The reset confirmation only confirms that the quota reset request completed; it does not imply an immediate overlay will appear.

## Tests

1. A coordinator test proves that a pending reminder ignores a temporary non-target foreground change and leaves the target session open without dismissing it.
2. A registry/lifecycle-focused test proves pending state survives an Activity stop and is cleared on an explicit action or destruction.
3. A Home ViewModel/UI test proves reset exposes a one-time success event only after the reset use case completes.
4. Existing reminder scheduler, coordinator, and full JVM test suites remain green.

## Scope

This is a reliability fix only.  The later optional “force a choice” setting, application-group UI, QR feedback, and sponsorship surfaces remain separate roadmap tasks.
