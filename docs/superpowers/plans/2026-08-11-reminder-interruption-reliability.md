# Reminder Interruption Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep an unresolved reminder and its target-app session intact through a temporary popup, and visibly confirm quota reset.

**Architecture:** `ReminderPresentationRegistry` represents a pending reminder. Detection services forward that state to the coordinator. User actions clear it before leaving; Activity `onStop` does not. Home emits a one-shot reset-complete event after persistence completes.

**Tech Stack:** Kotlin, Android AccessibilityService, UsageStats, Jetpack Compose, Hilt, coroutines, JUnit.

## Global Constraints

- Reset clears only reminder marks in its configured window; it never clears AI cache, history, or tasks.
- Do not change app groups, guardian policy, forced-choice setting, feedback, or sponsorship features.
- Do not stage `.idea`, `local.properties`, `.gradle-user-home`, or generated outputs.
- Observe each new regression test fail before changing production code.

---

### Task 1: Keep reminder pending during an unrelated popup

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/AppDetectionService.kt`
- Modify: `app/src/main/java/com/example/focus_app/service/ReminderActivity.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt`
- Test: `app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt`
- Test: `app/src/test/java/com/example/focus_app/service/ReminderPresentationPolicyTest.kt`

**Interfaces:**
- Consumes `ReminderPresentationRegistry.show(sessionId)`, `hide(sessionId)`, and `isShowing()`.
- Uses `AppSessionCoordinator.onPackageChanged(packageName, foregroundVerifier, isReminderPresentation)`.

- [x] **Step 1: Add the regression tests**

```kotlin
@Test
fun pending_reminder_ignores_a_temporary_foreground_popup() = runTest {
    val fixture = fixture()
    fixture.coordinator.onPackageChanged(TARGET_A)
    val sessionId = fixture.repository.sessions.single().id
    fixture.coordinator.onPackageChanged(
        packageName = "com.android.permissioncontroller",
        isReminderPresentation = true
    )
    assertNull(fixture.repository.sessions.single().endedAt)
    assertEquals(emptyList<Long>(), fixture.reminderScheduler.cancelledSessionIds)
    assertEquals(sessionId, fixture.repository.currentOpenSession()?.id)
}
```

Add a policy test asserting `isReminderPresentationForForegroundChange(true)` is true and `isReminderPresentationForForegroundChange(false)` is false.

- [x] **Step 2: Run the focused tests before service and lifecycle changes**

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.service.AppSessionCoordinatorTest" --tests "com.example.focus_app.service.ReminderPresentationPolicyTest"
```

Expected: compilation failure for the missing `isReminderPresentationForForegroundChange` policy function before the production implementation is added.

- [x] **Step 3: Add minimal pending-state wiring**

1. Inject `ReminderPresentationRegistry` into `FocusAccessibilityService`; set `PackageChange.isReminderPresentation` to `reminderPresentationRegistry.isShowing()`.
2. In `AppDetectionService`, pass `reminderPresentationRegistry.isShowing()` without checking the foreground package is Focus.
3. In `ReminderActivity`, retain `show` in `onStart`, remove `hide` from `onStop`, and call `hide` in `onDestroy`.
4. In `ReminderViewModel`, inject the registry and call `hide(sessionId)` before each successful return/follow-up action, but not after a custom-return error.

- [x] **Step 4: Run focused tests and commit**

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.service.AppSessionCoordinatorTest" --tests "com.example.focus_app.service.ReminderPresentationPolicyTest"
git add app/src/main/java/com/example/focus_app/service/FocusAccessibilityService.kt app/src/main/java/com/example/focus_app/service/AppDetectionService.kt app/src/main/java/com/example/focus_app/service/ReminderActivity.kt app/src/main/java/com/example/focus_app/ui/reminder/ReminderViewModel.kt app/src/test/java/com/example/focus_app/service/AppSessionCoordinatorTest.kt app/src/test/java/com/example/focus_app/service/ReminderPresentationRegistryTest.kt
git commit -m "fix: preserve reminders through transient popups"
```

### Task 2: Confirm reminder-quota reset on Home

**Files:**
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt`
- Test: `app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt`

**Interfaces:**
- Produces `HomeViewModel.events: SharedFlow<HomeEvent>` where `HomeEvent.ReminderQuotaReset` is emitted only after `ResetReminderQuotaUseCase.invoke()` returns.

- [x] **Step 1: Add a failing reset event test**

Use a suspending reset-use-case seam. Start `resetReminderQuota()`, assert no event while the seam is blocked, release it, then assert:

```kotlin
assertEquals(HomeEvent.ReminderQuotaReset, event)
assertEquals(1, resetCalls)
```

- [x] **Step 2: Verify RED**

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest"
```

Expected: compilation error for missing `events` and `HomeEvent`.

- [x] **Step 3: Implement only the event and confirmation**

1. Add `HomeEvent.ReminderQuotaReset` and a buffered `MutableSharedFlow` exposed as `events`.
2. Emit the event after `resetReminderQuotaUseCase()` returns.
3. Collect events in `HomeScreen` and show a `SnackbarHostState` message: `提醒额度已重置；下次进入目标应用后会按延迟提醒`.

- [x] **Step 4: Verify and commit**

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --no-configuration-cache --tests "com.example.focus_app.ui.home.HomeViewModelGuardianTest"
git add app/src/main/java/com/example/focus_app/ui/home/HomeViewModel.kt app/src/main/java/com/example/focus_app/ui/home/HomeScreen.kt app/src/test/java/com/example/focus_app/ui/home/HomeViewModelGuardianTest.kt
git commit -m "fix: confirm reminder quota reset"
```

### Task 3: Full verification

**Files:**
- Modify: `docs/superpowers/plans/2026-08-11-reminder-interruption-reliability.md`

- [x] **Step 1: Run full JVM tests and debug APK build**

```powershell
$env:GRADLE_USER_HOME='C:\Users\6\.gradle'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --no-configuration-cache
```

- [x] **Step 2: Mark tasks complete and commit verification record**

```powershell
git diff --check
git status --short
git add docs/superpowers/plans/2026-08-11-reminder-interruption-reliability.md
git commit -m "docs: record reminder interruption verification"
```
