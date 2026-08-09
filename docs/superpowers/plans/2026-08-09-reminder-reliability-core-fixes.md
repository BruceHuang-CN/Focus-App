# Reminder Reliability Core Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure an ended target-app session can neither display nor consume a reminder, and make “return to specified app” succeed visibly or fail with an actionable explanation.

**Architecture:** Keep the existing event-driven monitor and `AppUsageSession` model. Extend the existing scheduler/launcher boundary so closing a session cancels delayed work and any already-presented notification or reminder activity. Make the database update reject ended sessions, and give the reminder UI a final session-validity guard. Change the custom-app launcher from a silent `Unit` operation to a small result type that the ViewModel can present to the user.

**Tech Stack:** Kotlin, Room, Jetpack Compose, Hilt, Coroutines, Android notifications/intents, JUnit, Android instrumentation compilation.

## Global Constraints

- Do not introduce a new polling loop, backend service, account system, or force-stop API.
- Realtime monitoring remains accessibility-event driven; compatibility polling remains only the existing 10-second fallback.
- Do not log API keys, AI reminder text, task titles, or user feedback.
- Preserve existing session statistics and target-app selection behavior.
- A failed custom-app launch must keep the reminder visible and must not be recorded as a successful active exit.
- Use test-driven development: every production behavior below starts with a new test that fails for the expected missing behavior.
- Commit only focused source, test, and documentation changes; do not commit APKs, `local.properties`, keys, `.gradle`, or IDE files.

---

## File Structure

| File | Responsibility |
|---|---|
| `service/ReminderLauncher.kt` | Launch, dismiss, and classify custom-app return actions. |
| `service/ReminderScheduler.kt` | Cancel all presentation layers on session close and gate reminder writes against ended sessions. |
| `service/ReminderActivity.kt` | Reject stale reminder intents and finish on a same-session dismiss broadcast. |
| `service/ReminderPresentationPolicy.kt` | Pure overlay/notification and stale-session presentation helpers extracted from the launcher. |
| `data/local/dao/AppUsageSessionDao.kt` | Atomically reject `remindedAt` writes for ended sessions. |
| `ui/reminder/ReminderViewModel.kt` | Expose a custom-return error and only record a custom exit after a successful launch. |
| `ui/reminder/ReminderOverlay.kt` | Render a concise error and an explicit desktop fallback. |
| `service/ReminderSchedulerTest.kt` | Regression coverage for closed-session quota and cancellation behavior. |
| `service/ReminderPresentationPolicyTest.kt` | Pure stale-session presentation coverage. |
| `ui/reminder/ReminderViewModelTest.kt` | Custom launch success/failure behavior. |
| `DEVELOPMENT_LOG.md` | Record confirmed causes, fixes, and exact verification results after implementation. |

---

### Task 1: Make reminder quota writes reject ended sessions

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/data/local/dao/AppUsageSessionDao.kt`
- Modify: `app/src/main/java/com/example/focus_app/data/repository/AppSessionRepository.kt`
- Test: `app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `AppUsageSession.endedAt`, `AppSessionRepository.currentOpenSession()`.
- Produces: `AppSessionRepository.markRemindedIfNeeded(sessionId, remindedAt)` returns `false` when the persisted session has already ended.
- Produces: `AppSessionRepository.updateRemindedAt(sessionId, remindedAt)` updates only an unended session.

- [ ] **Step 1: Write the failing scheduler regression test**

In `ReminderSchedulerTest`, add a test whose fake repository rejects no ended writes yet:

```kotlin
@Test
fun closed_session_cannot_reserve_a_reminder_quota() = runTest {
    val fixture = fixture(closeImmediatelyBeforeReminderMark = true)
    fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
    advanceTimeBy(10_001L)
    runCurrent()

    assertEquals(0, fixture.launcher.shown.size)
    assertEquals(null, fixture.repository.session(fixture.session.id)?.remindedAt)
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderSchedulerTest.closed_session_cannot_reserve_a_reminder_quota"
```

Expected: FAIL because the fake closes the session immediately before `markRemindedIfNeeded`, but the current write implementation still accepts that ended session and launches a reminder.

- [ ] **Step 3: Add the database-level ended-session guard**

Change the DAO predicates to require an unended session:

```kotlin
@Query(
    "UPDATE app_usage_sessions SET remindedAt = :remindedAt " +
        "WHERE id = :sessionId AND remindedAt IS NULL AND endedAt IS NULL"
)
suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Int

@Query(
    "UPDATE app_usage_sessions SET remindedAt = :remindedAt " +
        "WHERE id = :sessionId AND endedAt IS NULL"
)
suspend fun updateRemindedAt(sessionId: Long, remindedAt: Long): Int
```

Keep the repository method names; `RoomAppSessionRepository` continues converting the first DAO result to Boolean. Add `closeImmediatelyBeforeReminderMark` to the scheduler-test fake; when true, it closes the target session at the start of `markRemindedIfNeeded`. The fake must then return `false` when `endedAt != null`, matching the new DAO condition.

- [ ] **Step 4: Run the focused test and the existing scheduler suite**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderSchedulerTest"
```

Expected: all scheduler tests pass, including the new closed-session quota regression.

- [ ] **Step 5: Commit the atomic quota guard**

```powershell
git add app/src/main/java/com/example/focus_app/data/local/dao/AppUsageSessionDao.kt app/src/main/java/com/example/focus_app/data/repository/AppSessionRepository.kt app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt
git commit -m "fix: prevent ended sessions from using reminder quota"
```

### Task 2: Dismiss every presentation when a session closes

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- Create: `app/src/main/java/com/example/focus_app/service/ReminderPresentationPolicy.kt`
- Test: `app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/ReminderPresentationPolicyTest.kt`
- Test support: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`

**Interfaces:**
- Produces: `ReminderLauncher.dismiss(sessionId: Long)`.
- Produces: `internal fun isReminderSessionCurrent(sessionId: Long, currentOpenSessionId: Long?): Boolean`.
- Consumes: `SessionReminderScheduler.cancel(sessionId)` from `AppSessionCoordinator`.

- [ ] **Step 1: Write the failing cancellation tests**

Add one scheduler test and one pure presentation-policy test:

```kotlin
@Test
fun cancel_dismisses_an_already_presented_session() {
    val fixture = fixture()

    fixture.scheduler.cancel(fixture.session.id)

    assertEquals(listOf(fixture.session.id), fixture.launcher.dismissedSessionIds)
}

@Test
fun ended_session_is_not_a_valid_reminder_presentation() {
    assertEquals(false, isReminderSessionCurrent(12L, currentOpenSessionId = null))
    assertEquals(false, isReminderSessionCurrent(12L, currentOpenSessionId = 13L))
    assertEquals(true, isReminderSessionCurrent(12L, currentOpenSessionId = 12L))
}
```

- [ ] **Step 2: Run the focused tests and verify they fail because the API is missing**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderSchedulerTest.cancel_dismisses_an_already_presented_session" --tests "*ReminderPresentationPolicyTest.ended_session_is_not_a_valid_reminder_presentation"
```

Expected: compilation fails because `dismiss`, `dismissedSessionIds`, or `isReminderSessionCurrent` does not yet exist.

- [ ] **Step 3: Implement cancellation and stale-activity protection**

Add the launcher method:

```kotlin
interface ReminderLauncher {
    fun show(data: ReminderLaunchData)
    fun dismiss(sessionId: Long)
    fun returnToFocus(taskId: Long?)
    fun returnHome()
    fun returnToCustom(packageName: String) = Unit
}
```

Move the existing `presentReminder` helper into the new `ReminderPresentationPolicy.kt`. `AndroidReminderLauncher.dismiss` must cancel the notification ID generated from `sessionId`, then send a package-scoped broadcast using `ReminderActivity.ACTION_DISMISS_REMINDER` and `ReminderActivity.EXTRA_DISMISS_SESSION_ID`.

`ReminderScheduler.cancel` must always call `launcher.dismiss(sessionId)` after cancelling/removing its Job. `ReminderActivity` must register a receiver while started, call `finishAndRemoveTask()` only when the received session ID matches its launch data, and query `AppSessionRepository.currentOpenSession()` before calling `setContent`. If the query does not match the intent session ID, finish without rendering `ReminderOverlay`.

Keep `isReminderSessionCurrent` pure in `ReminderPresentationPolicy.kt` and cover it with the test above.

- [ ] **Step 4: Run the service and reminder ViewModel tests**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderSchedulerTest" --tests "*ReminderPresentationPolicyTest" --tests "*AppSessionCoordinatorTest" --tests "*ReminderViewModelTest"
```

Expected: all focused tests pass; leaving a target App continues to call `cancel(sessionId)`, which now also dismisses stale presentations.

- [ ] **Step 5: Commit session presentation cancellation**

```powershell
git add app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt app/src/main/java/com/example/focus_app/service/ReminderScheduler.kt app/src/main/java/com/example/focus_app/service/ReminderActivity.kt app/src/main/java/com/example/focus_app/service/ReminderPresentationPolicy.kt app/src/test/java/com/example/focus_app/service/ReminderSchedulerTest.kt app/src/test/java/com/example/focus_app/service/ReminderPresentationPolicyTest.kt app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt
git commit -m "fix: dismiss reminders when target sessions end"
```

### Task 3: Make custom return launches observable and safe

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/settings/CustomReturnAppPickerScreen.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt`

**Interfaces:**
- Produces: `sealed interface CustomAppLaunchResult { data object Launched; data object NotSelected; data object NotLaunchable; data class Failed(val reason: String) }`.
- Produces: `ReminderUiState.customReturnError: String?`.
- Consumes: the saved `returnPackageName` in `ReminderLaunchData`.

- [ ] **Step 1: Write the failing ViewModel tests for launch result handling**

Add success and failure behavior tests with a configurable fake launcher:

```kotlin
@Test
fun successful_custom_return_records_action_and_finishes() = runTest(dispatcher) {
    val fixture = fixture(customResult = CustomAppLaunchResult.Launched)
    fixture.viewModel.init(LAUNCH_DATA.copy(returnDestination = ReturnDestination.CUSTOM, returnPackageName = "com.tencent.mm"))

    fixture.viewModel.returnToCustom(LAUNCH_DATA.sessionId)
    advanceUntilIdle()

    assertEquals("returned_to_custom", fixture.repository.action)
    assertEquals(null, fixture.viewModel.uiState.value.customReturnError)
}

@Test
fun failed_custom_return_keeps_reminder_open_and_exposes_reason() = runTest(dispatcher) {
    val fixture = fixture(customResult = CustomAppLaunchResult.NotLaunchable)
    fixture.viewModel.init(LAUNCH_DATA.copy(returnDestination = ReturnDestination.CUSTOM, returnPackageName = "missing.app"))

    fixture.viewModel.returnToCustom(LAUNCH_DATA.sessionId)
    advanceUntilIdle()

    assertEquals(null, fixture.repository.action)
    assertEquals("指定应用不可启动，请重新选择。", fixture.viewModel.uiState.value.customReturnError)
}
```

- [ ] **Step 2: Run the focused tests and verify the expected missing-result failure**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderViewModelTest.successful_custom_return_records_action_and_finishes" --tests "*ReminderViewModelTest.failed_custom_return_keeps_reminder_open_and_exposes_reason"
```

Expected: compilation fails because `CustomAppLaunchResult` and `customReturnError` are absent.

- [ ] **Step 3: Implement result-based launch behavior**

In `AndroidReminderLauncher.returnToCustom`, return `NotSelected` for blank package names, `NotLaunchable` when `getLaunchIntentForPackage` returns null, and `Failed` when `startActivity` throws `ActivityNotFoundException` or `SecurityException`; otherwise return `Launched`.

In `ReminderViewModel.returnToCustom`, call the launcher first. Only after `Launched` should it write `returned_to_custom` and invoke `onComplete`. On every failure, retain the overlay and map the result to a concise Chinese error string.

In `ReminderOverlay`, render `customReturnError` below the action row and show a desktop fallback button only while the custom return error is non-null. In `CustomReturnAppPickerScreen`, keep filtering to launchable installed apps; add a visible empty state when no launchable app matches the search.

- [ ] **Step 4: Run the focused UI/ViewModel test suite**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest --tests "*ReminderViewModelTest" --tests "*SettingsViewModelDetectionTest"
```

Expected: all reminder ViewModel tests pass; failed custom launch is visible and is not counted as a successful active exit.

- [ ] **Step 5: Commit custom-return diagnostics**

```powershell
git add app/src/main/java/com/example/focus_app/service/ReminderLauncher.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderOverlay.kt app/src/main/java/com/example/focus_app/ui/settings/CustomReturnAppPickerScreen.kt app/src/test/java/com/example/focus_app/ui/reminder/ReminderViewModelTest.kt
git commit -m "fix: explain failed custom return launches"
```

### Task 4: Run regression verification and document evidence

**Files:**
- Modify: `DEVELOPMENT_LOG.md`
- Test: all existing unit tests
- AndroidTest compile: existing `app/src/androidTest`

**Interfaces:**
- Consumes all behavior from Tasks 1–3.
- Produces an evidence-backed development-log entry and a clean core-fix commit history.

- [ ] **Step 1: Write the development-log entry before final verification**

Add one dated entry that states: ended sessions now reject quota writes, session cancellation removes delayed jobs/notifications/active overlays, custom returns expose launch failures, and Android system accessibility is not programmatically enabled or disabled.

- [ ] **Step 2: Run the complete automated verification**

Run:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug
```

Expected: build succeeds; unit-test report contains no failed tests; Android test source compiles; Debug APK is generated locally and remains ignored by Git.

- [ ] **Step 3: Check the exact committed diff and repository cleanliness**

Run:

```powershell
git diff --check
git status --short
git log --oneline -5
```

Expected: no whitespace errors; only the development-log entry is uncommitted before the next step.

- [ ] **Step 4: Commit the verification record**

```powershell
git add DEVELOPMENT_LOG.md
git commit -m "docs: record reminder reliability verification"
git status --short
```

- [ ] **Step 5: Perform the required manual device checks**

On a device, test both detection modes: open a target App, return to the launcher, wait longer than the configured delay plus 10 seconds, and confirm there is no popup, notification, or quota increment. Then select an installed return App and verify the custom-return button opens it; repeat after making the selection invalid and confirm the overlay shows a failure message rather than silently returning home.

---

## Completion Criteria

- An ended session cannot be marked reminded or launch a new reminder.
- Leaving a target app removes its pending and already-presented reminder surfaces.
- A stale reminder intent never renders an overlay.
- Custom return only records success after a successful launch and makes failures visible.
- Existing monitoring and statistics behavior stay intact.
- Automated verification and the two device-mode checks are recorded in `DEVELOPMENT_LOG.md`.
