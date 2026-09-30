package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.FeedbackApi
import com.example.focus_app.domain.feedback.AppFeedbackRequest
import com.example.focus_app.domain.feedback.AppFeedbackResponse
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * 一次提交的结果。
 *
 * 只有服务端明确返回成功且回执与当前 id 一致时才是 [Saved]；其余情况都必须保留草稿。
 */
sealed interface FeedbackSubmitResult {
    data class Saved(val receipt: String) : FeedbackSubmitResult

    /** 服务端拒绝输入（400 / 413 / 415），不自动重试。 */
    data class Rejected(val code: String) : FeedbackSubmitResult

    /** 同一 id 对应不同内容（409），不覆盖原反馈。 */
    data object Conflict : FeedbackSubmitResult

    data class RateLimited(val retryAfterSeconds: Long) : FeedbackSubmitResult

    /** 未启用、网络失败、超时、非 JSON 或回执不一致，都属于“暂未确认收到”。 */
    data object Unavailable : FeedbackSubmitResult
}

@Singleton
class FeedbackRepository @Inject constructor(
    private val api: FeedbackApi
) {
    private val gson = Gson()

    suspend fun submit(payload: AppFeedbackRequest): FeedbackSubmitResult = try {
        interpret(payload, api.submit(payload))
    } catch (cancelled: CancellationException) {
        // 取消必须正常传播，不能被兜底成网络失败。
        throw cancelled
    } catch (_: Exception) {
        FeedbackSubmitResult.Unavailable
    }

    private fun interpret(
        payload: AppFeedbackRequest,
        response: Response<AppFeedbackResponse>
    ): FeedbackSubmitResult {
        val body = response.body()
        return when {
            response.code() == 429 -> FeedbackSubmitResult.RateLimited(response.retryAfterSeconds())
            response.isSuccessful && body?.ok == true && body.receipt == payload.id ->
                FeedbackSubmitResult.Saved(payload.id)
            response.code() == 409 -> FeedbackSubmitResult.Conflict
            response.code() == 400 || response.code() == 413 || response.code() == 415 ->
                FeedbackSubmitResult.Rejected(errorCode(response) ?: defaultRejectionCode(response.code()))
            // 200/201 但 JSON 破损、ok 不为 true 或回执不一致，一律视为未确认收到。
            else -> FeedbackSubmitResult.Unavailable
        }
    }

    /** 错误码在 errorBody 中，而不是 body()；解析失败时按状态码给保守默认值。 */
    private fun errorCode(response: Response<*>): String? = runCatching {
        gson.fromJson(response.errorBody()?.string(), AppFeedbackResponse::class.java)?.code
    }.getOrNull()

    private fun defaultRejectionCode(code: Int): String = when (code) {
        413 -> "PAYLOAD_TOO_LARGE"
        415 -> "UNSUPPORTED_MEDIA_TYPE"
        else -> "INVALID_PAYLOAD"
    }
}

private fun Response<*>.retryAfterSeconds(): Long =
    headers()["Retry-After"]?.trim()?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
