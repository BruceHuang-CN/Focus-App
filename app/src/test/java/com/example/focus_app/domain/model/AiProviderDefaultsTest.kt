package com.example.focus_app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderDefaultsTest {
    @Test
    fun compatible_provider_base_paths_include_v1() {
        assertEquals("https://api.openai.com/v1", AiProvider.OPENAI.defaultEndpoint)
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            AiProvider.QWEN.defaultEndpoint
        )
        assertEquals("https://api.deepseek.com", AiProvider.DEEPSEEK.defaultEndpoint)
        assertEquals("deepseek-v4-flash", AiProvider.DEEPSEEK.defaultModel)
    }
}
