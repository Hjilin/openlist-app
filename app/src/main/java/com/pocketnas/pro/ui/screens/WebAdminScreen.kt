package com.pocketnas.pro.ui.screens

import android.webkit.WebSettings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pocketnas.pro.ui.AppViewModel

/**
 * 网页管理后台：加载 App 内置的自绘后台（admin_panel.html，真实内核对接版）。
 * 统一账号：App 已登录时把同一 token 注入 localStorage['openlist_token']，
 * 后台 JS 初始化时读取并作为 Authorization 头直连内核，打开即免登录，
 * 与「添加存储源」登录门共用同一内核账号体系。
 * 不再加载原生 OpenList 前端（其登录态不读 localStorage，注入 token 无效且内容区有遮罩）。
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun WebAdminScreen(vm: AppViewModel, onBack: () -> Unit) {
    val token by vm.token.collectAsState()
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 统一账号：注入 App 同一 token，后台 JS 启动时读取（幂等，相同不重复刷新）
                                val t = token ?: ""
                                if (t.isNotBlank()) {
                                    // token 为 JWT（base64url），不含引号/反斜杠，直接拼 JS 字面量安全
                                    view?.evaluateJavascript(
                                        "javascript:(function(){" +
                                                "try{" +
                                                "var want=\"$t\";" +
                                                "if(localStorage.getItem('openlist_token')!==want){" +
                                                "localStorage.setItem('openlist_token',want);location.reload();" +
                                                "}" +
                                                "}catch(e){}})()",
                                        null
                                    )
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 自绘后台在 file:// 下 fetch 内核 http://127.0.0.1:5244，需放开混合内容
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        // 加载 App 内置后台（自绘、真实对接内核）
                        loadUrl("file:///android_asset/admin_panel.html")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
