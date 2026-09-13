package com.example.focus_app.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.focus_app.R

internal const val FEEDBACK_SURVEY_URL = "https://v.wjx.cn/vm/Y9RWvc9.aspx#"

internal enum class SupportPaymentMethod {
    WECHAT,
    ALIPAY
}

internal data class SupportPaymentContent(
    @StringRes val label: Int,
    @StringRes val hint: Int,
    @StringRes val contentDescription: Int,
    @DrawableRes val qrResource: Int
)

internal fun supportPaymentContent(method: SupportPaymentMethod): SupportPaymentContent =
    when (method) {
        SupportPaymentMethod.WECHAT -> SupportPaymentContent(
            label = R.string.setup_text_167,
            hint = R.string.setup_text_168,
            contentDescription = R.string.setup_text_169,
            qrResource = R.drawable.qr_wechat_support
        )

        SupportPaymentMethod.ALIPAY -> SupportPaymentContent(
            label = R.string.setup_text_170,
            hint = R.string.setup_text_171,
            contentDescription = R.string.setup_text_172,
            qrResource = R.drawable.qr_alipay_support
        )
    }
