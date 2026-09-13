package com.example.focus_app.ui.onboarding

import androidx.compose.runtime.Composable

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    TutorialScreen(onComplete = onComplete)
}
