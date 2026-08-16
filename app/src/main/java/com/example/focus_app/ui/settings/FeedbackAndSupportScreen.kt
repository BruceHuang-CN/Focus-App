package com.example.focus_app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.focus_app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackAndSupportScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var surveyOpenFailed by rememberSaveable { mutableStateOf(false) }
    var selectedPaymentName by rememberSaveable {
        mutableStateOf(SupportPaymentMethod.WECHAT.name)
    }
    val selectedPayment = if (selectedPaymentName == SupportPaymentMethod.ALIPAY.name) {
        SupportPaymentMethod.ALIPAY
    } else {
        SupportPaymentMethod.WECHAT
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("反馈与支持") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "你的建议会帮助 Focus 变得更好。",
                style = MaterialTheme.typography.bodyLarge
            )
            FeedbackCard(
                surveyOpenFailed = surveyOpenFailed,
                onOpenSurvey = {
                    surveyOpenFailed = runCatching {
                        uriHandler.openUri(FEEDBACK_SURVEY_URL)
                    }.isFailure
                }
            )
            SupportCard(
                selectedPayment = selectedPayment,
                onPaymentSelected = { selectedPaymentName = it.name }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FeedbackCard(
    surveyOpenFailed: Boolean,
    onOpenSurvey: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "提交反馈",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                painter = painterResource(R.drawable.questionnaire_poster),
                contentDescription = "Focus 用户反馈调查海报",
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .aspectRatio(804f / 1072f),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenSurvey,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("填写问卷")
            }
            if (surveyOpenFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂时无法打开链接，请确认手机已安装浏览器后重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SupportCard(
    selectedPayment: SupportPaymentMethod,
    onPaymentSelected: (SupportPaymentMethod) -> Unit
) {
    val content = supportPaymentContent(selectedPayment)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "支持 Focus",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "如果 Focus 对你有帮助，可以自愿支持后续开发与维护。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethodButton(
                    label = "微信",
                    selected = selectedPayment == SupportPaymentMethod.WECHAT,
                    onClick = { onPaymentSelected(SupportPaymentMethod.WECHAT) },
                    modifier = Modifier.weight(1f)
                )
                PaymentMethodButton(
                    label = "支付宝",
                    selected = selectedPayment == SupportPaymentMethod.ALIPAY,
                    onClick = { onPaymentSelected(SupportPaymentMethod.ALIPAY) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = content.hint,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .aspectRatio(1f),
                color = Color.White,
                shape = MaterialTheme.shapes.medium
            ) {
                Image(
                    painter = painterResource(content.qrResource),
                    contentDescription = content.contentDescription,
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "可使用另一台设备扫码，或截图后在对应 App 中识别。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentMethodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
