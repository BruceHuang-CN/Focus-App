package com.example.focus_app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackAndSupportScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u53cd\u9988\u4e0e\u652f\u6301") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "\u8fd4\u56de"
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
                text = "\u4f60\u7684\u5efa\u8bae\u4f1a\u5e2e\u52a9 Focus \u53d8\u5f97\u66f4\u597d\u3002",
                style = MaterialTheme.typography.bodyLarge
            )
            PlaceholderCard(
                title = "\u63d0\u4ea4\u53cd\u9988",
                description = "\u8bf7\u5728\u6b64\u5904\u653e\u5165\u95ee\u5377\u661f\u6216\u5fae\u4fe1\u516c\u4f17\u53f7\u7684\u4e8c\u7ef4\u7801\u3001\u94fe\u63a5\u3002\u672a\u63d0\u4f9b\u524d\u4e0d\u5c55\u793a\u4efb\u4f55\u8054\u7cfb\u65b9\u5f0f\u3002"
            )
            PlaceholderCard(
                title = "\u652f\u6301 Focus",
                description = "\u8bf7\u5728\u6b64\u5904\u653e\u5165\u8d5e\u52a9\u4e8c\u7ef4\u7801\u6216\u652f\u4ed8\u94fe\u63a5\u3002\u672a\u63d0\u4f9b\u6536\u6b3e\u4fe1\u606f\u524d\uff0c\u9875\u9762\u4e0d\u4f1a\u5f15\u5bfc\u4efb\u4f55\u8d5e\u52a9\u3002"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlaceholderCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
