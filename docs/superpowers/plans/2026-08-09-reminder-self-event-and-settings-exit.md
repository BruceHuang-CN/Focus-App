# Reminder Self-Event and Settings Exit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a reminder visible when Focus launches its own reminder activity, and ask before leaving genuinely unsaved settings.

**Architecture:** Treat a Focus-owned reminder activity as a presentation event, not a user exit from the target app. The realtime detector recognizes `ReminderActivity` directly, while compatibility mode uses a small injected registry during its 10-second foreground observation. Keep existing immediate settings writes, but capture an entry snapshot so an explicit discard action can restore every ordinary settings value changed on the page.

**Tech Stack:** Kotlin, Android AccessibilityService, UsageStats foreground service, Jetpack Compose, StateFlow, JUnit.

## Global Constraints

- Do not disable Android accessibility programmatically.
- Preserve target-app usage recording and the existing 10-second compatibility polling interval.
- Avoid new libraries, background loops, or network calls.
- Use failing tests before production changes.

---

### Task 1: Prevent Focus-owned reminder windows from ending their own sessions

**Files:**
- Create: `app/src/main/java/com/example/focus_app/service/ReminderPresentationRegistry.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppSessionCoordinator.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt`

- [x] Write a failing coordinator test proving a package change marked as a Focus reminder presentation keeps the target session open.
- [x] Run the focused test and verify it fails because no presentation-event input exists.
- [x] Add a singleton registry that records the active reminder session only while `ReminderActivity` is started.
- [x] Pass the registry state to both detection services; classify only the matching Focus package event as a presentation event.
- [x] Make `AppSessionCoordinator` ignore that one presentation event while still closing sessions for all user-driven package changes.
- [x] Run coordinator, scheduler, and reminder presentation tests.

### Task 2: Make settings exit confirmation real

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/settings/SettingsViewModelTest.kt`

- [x] Write a failing ViewModel test proving a discard operation restores the entry snapshot after immediate updates.
- [x] Run the focused test and verify it fails because no restore operation exists.
- [x] Add an entry snapshot and `restoreExitSnapshot()` operation without changing API key persistence.
- [x] Add a Compose confirmation dialog on back navigation with Save and exit, Discard changes, and Continue editing actions.
- [x] Run settings ViewModel tests and compile Android tests.

### Task 3: Verify and document

**Files:**
- Modify: `DEVELOPMENT_LOG.md`

- [x] Run `testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`.
- [x] Check `git diff --check` and inspect the staged diff.
- [x] Add a dated development-log entry describing both fixes and the required manual device checks.
- [ ] Commit the focused changes on `feature/focus-v0.1-preview` without pushing.
