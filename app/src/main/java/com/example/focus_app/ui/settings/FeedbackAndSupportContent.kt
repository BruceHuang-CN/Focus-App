package com.example.focus_app.ui.settings

import androidx.annotation.DrawableRes
import com.example.focus_app.R

internal const val FEEDBACK_SURVEY_URL = "https://v.wjx.cn/vm/Y9RWvc9.aspx#"

internal enum class SupportPaymentMethod {
    WECHAT,
    ALIPAY
}

internal data class SupportPaymentContent(
    val label: String,
    val hint: String,
    val contentDescription: String,
    @DrawableRes val qrResource: Int
)

internal fun supportPaymentContent(method: SupportPaymentMethod): SupportPaymentContent =
    when (method) {
        SupportPaymentMethod.WECHAT -> SupportPaymentContent(
            label = "微信",
            hint = "请使用微信扫码支持 Focus",
            contentDescription = "微信收款二维码",
            qrResource = R.drawable.qr_wechat_support
        )

        SupportPaymentMethod.ALIPAY -> SupportPaymentContent(
            label = "支付宝",
            hint = "请使用支付宝扫码支持 Focus",
            contentDescription = "支付宝收款二维码",
            qrResource = R.drawable.qr_alipay_support
        )
    }
