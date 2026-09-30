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
fun FeedbackAndSupportScreen(
    onBack: () -> Unit,
    onReportIssue: () -> Unit
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
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
                title = { Text(setupContext.getString(R.string.setup_text_173)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = setupContext.getString(R.string.setup_text_164)
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
                text = setupContext.getString(R.string.setup_text_174),
                style = MaterialTheme.typography.bodyLarge
            )
            ReportIssueEntryCard(onReportIssue = onReportIssue)
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
private fun ReportIssueEntryCard(onReportIssue: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = setupContext.getString(R.string.feedback_report_entry_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = setupContext.getString(R.string.feedback_report_entry_description),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onReportIssue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(setupContext.getString(R.string.feedback_report_entry_action))
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    surveyOpenFailed: Boolean,
    onOpenSurvey: () -> Unit
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = setupContext.getString(R.string.setup_text_175),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                painter = painterResource(R.drawable.questionnaire_poster),
                contentDescription = setupContext.getString(R.string.setup_text_176),
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
                Text(setupContext.getString(R.string.setup_text_177))
            }
            if (surveyOpenFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = setupContext.getString(R.string.setup_text_178),
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
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val content = supportPaymentContent(selectedPayment)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = setupContext.getString(R.string.setup_text_179),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = setupContext.getString(R.string.setup_text_180),
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
                    label = setupContext.getString(R.string.setup_text_167),
                    selected = selectedPayment == SupportPaymentMethod.WECHAT,
                    onClick = { onPaymentSelected(SupportPaymentMethod.WECHAT) },
                    modifier = Modifier.weight(1f)
                )
                PaymentMethodButton(
                    label = setupContext.getString(R.string.setup_text_170),
                    selected = selectedPayment == SupportPaymentMethod.ALIPAY,
                    onClick = { onPaymentSelected(SupportPaymentMethod.ALIPAY) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = setupContext.getString(content.hint),
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
                    contentDescription = setupContext.getString(content.contentDescription),
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = setupContext.getString(R.string.setup_text_181),
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
