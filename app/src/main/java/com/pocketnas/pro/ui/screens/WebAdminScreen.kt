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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pocketnas.pro.ui.AppViewModel

/**
 * OpenList 原生网页管理后台（WebView 加载 127.0.0.1:5244）。
 * 与原版 AList/OpenList 功能一致：存储源、用户、设置、离线下载等全部在网页里操作。
 * 统一账号：App 已登录时把同一 token 注入网页 localStorage，打开后台即免登录，
 * 与「添加存储源」登录门共用同一内核账号体系。
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
                                // 统一账号：注入 App 同一 token，打开即免登录（幂等，token 相同不重复刷新）
                                val t = token ?: ""
                                if (t.isNotBlank()) {
                                    // token 为 JWT（base64url），不含引号/反斜杠，直接拼 JSON 安全
                                val jsToken = "{\"t\":\"$t\"}"
                                    view?.evaluateJavascript(
                                        "javascript:(function(){" +
                                                "try{" +
                                                "var want=" + jsToken + ".t;" +
                                                "if(localStorage.getItem('token')!==want){" +
                                                "localStorage.setItem('token',want);location.reload();" +
                                                "}" +
                                                "}catch(e){}})()",
                                        null
                                    )
                                }
                                // OpenList 登录卡片在窄视口下可能超出顶部被裁，加载后滚到表单起始位置
                                view?.loadUrl(
                                    "javascript:(function(){" +
                                            "try{window.scrollTo(0,0);" +
                                            "var f=document.querySelector('form')||document.querySelector('input');" +
                                            "if(f&&f.scrollIntoView){f.scrollIntoView({block:'start'});}" +
                                            "}catch(e){}})()"
                                )
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 移动模式窄视口渲染，让 openlist 响应式页面正常显示登录表单
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
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
