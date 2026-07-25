package com.example.focus_app.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.domain.usecase.RecordMoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

val MOOD_OPTIONS = listOf("\uD83D\uDE34 困了", "\uD83D\uDE2B 焦虑", "\uD83E\uDD71 无聊", "\uD83D\uDE24 烦躁", "\u26A1 有干劲", "\uD83D\uDE0A 还行", "\uD83D\uDE22 难过", "\uD83E\uDD14 迷茫")

@HiltViewModel
class MoodViewModel @Inject constructor(private val recordMoodUseCase: RecordMoodUseCase) : ViewModel() {
    fun saveMood(mood: String, note: String? = null, onDone: () -> Unit) { viewModelScope.launch { recordMoodUseCase(mood, note); onDone() } }
}
