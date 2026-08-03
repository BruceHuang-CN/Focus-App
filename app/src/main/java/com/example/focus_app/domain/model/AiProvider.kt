package com.example.focus_app.domain.model

enum class AiProvider(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo"),
    CUSTOM("自定义", "", "");
    companion object {
        fun fromKey(key: String) = entries.find { it.name.equals(key, ignoreCase = true) } ?: CUSTOM
    }
}
