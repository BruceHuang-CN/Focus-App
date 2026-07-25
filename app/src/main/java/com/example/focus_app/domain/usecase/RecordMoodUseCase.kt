package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.MoodRepository
import javax.inject.Inject

class RecordMoodUseCase @Inject constructor(private val moodRepository: MoodRepository) {
    suspend operator fun invoke(mood: String, note: String? = null) { moodRepository.recordMood(mood, note) }
}
