package com.pocketnas.pro.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * OpenList 原生网页管理后台（WebView 加载 127.0.0.1:5244）。
 * 与原版 AList/OpenList 功能一致：存储源、用户、设置、离线下载等全部在网页里操作。
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun WebAdminScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页管理后台") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // 加载本地 OpenList 内核网页
                        loadUrl("http://127.0.0.1:5244/")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
