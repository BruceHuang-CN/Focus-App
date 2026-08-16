package com.example.focus_app.ui.settings

import com.example.focus_app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackAndSupportContentTest {

    @Test
    fun `wechat selection uses only the wechat qr code`() {
        val content = supportPaymentContent(SupportPaymentMethod.WECHAT)

        assertEquals("微信", content.label)
        assertEquals(R.drawable.qr_wechat_support, content.qrResource)
    }

    @Test
    fun `alipay selection uses only the alipay qr code`() {
        val content = supportPaymentContent(SupportPaymentMethod.ALIPAY)

        assertEquals("支付宝", content.label)
        assertEquals(R.drawable.qr_alipay_support, content.qrResource)
    }

    @Test
    fun `feedback button opens the supplied questionnaire`() {
        assertEquals(
            "https://v.wjx.cn/vm/Y9RWvc9.aspx#",
            FEEDBACK_SURVEY_URL
        )
    }
}
