package com.example.focus_app.data.remote

import java.io.IOException
import java.net.SocketTimeoutException

/** Safe user-facing errors: never expose server bodies, credentials or endpoint query strings. */
enum class AiFailure {
    MISSING_KEY, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_OR_QUOTA, SERVER, TIMEOUT, NETWORK, INVALID_RESPONSE, REQUEST;

    fun message(languageTag: String): String {
        val english = languageTag.startsWith("en", ignoreCase = true)
        return when (this) {
            MISSING_KEY -> if (english) "Save your API Key first." else "请先保存 API Key"
            UNAUTHORIZED -> if (english) "The API Key is invalid or does not match the API address." else "API Key 无效，或与 API 地址不匹配"
            FORBIDDEN -> if (english) "This API Key does not have permission to access this service or model." else "当前 API Key 没有访问该服务或模型的权限"
            NOT_FOUND -> if (english) "The API address, interface or model was not found. Check your configuration." else "API 地址、接口或模型不存在，请检查配置"
            RATE_OR_QUOTA -> if (english) "The service limited the request. Check your balance, quota and request rate, then retry." else "服务限制了此次请求，请检查余额、额度和请求频率后重试"
            SERVER -> if (english) "The AI service is temporarily unavailable. Try again later." else "AI 服务暂时不可用，请稍后重试"
            TIMEOUT -> if (english) "The connection timed out. Check your network and API address." else "连接超时，请检查网络和 API 地址"
            NETWORK -> if (english) "Could not connect. Check your network and API address." else "网络连接失败，请检查网络和 API 地址"
            INVALID_RESPONSE -> if (english) "The service returned an unexpected format. Check interface compatibility." else "服务返回的格式不符合要求，请检查接口兼容性"
            REQUEST -> if (english) "The request failed. Check your configuration and try again." else "请求失败，请检查配置后重试"
        }
    }

    companion object {
        fun fromHttp(code: Int): AiFailure = when (code) {
            401 -> UNAUTHORIZED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            429 -> RATE_OR_QUOTA
            in 500..599 -> SERVER
            else -> REQUEST
        }
        fun fromException(error: Exception): AiFailure = when (error) {
            is SocketTimeoutException -> TIMEOUT
            is IOException -> NETWORK
            else -> REQUEST
        }
    }
}
