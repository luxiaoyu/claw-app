package com.moonshot.kimiclaw.openclaw

import com.termux.shared.logger.Logger
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * KimiClaw WebView 封装类
 * 负责 OpenClaw Dashboard WebView 的创建、配置和业务逻辑
 */
class KimiClawWebView private constructor(
    private val webView: WebView
) {
    companion object {
        private const val LOG_TAG = "KimiClawWebView"
        private const val SETTINGS_KEY = "openclaw.control.settings.v1"
        private const val DEFAULT_URL = "http://127.0.0.1:18789/"

        /**
         * 创建并配置 KimiClawWebView
         *
         * @param context Context
         * @param url 要加载的 URL
         * @param gatewayToken Gateway Token 用于自动认证
         * @param callbacks WebView 回调
         * @return 配置好的 KimiClawWebView 实例
         */
        @SuppressLint("SetJavaScriptEnabled")
        fun create(
            context: Context,
            url: String = DEFAULT_URL,
            gatewayToken: String? = null,
            callbacks: WebViewCallbacks
        ): KimiClawWebView {
            Logger.logDebug(LOG_TAG, "Creating KimiClawWebView, token available: ${gatewayToken != null}")

            val webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // 配置 WebView 设置
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // 设置 WebViewClient
                webViewClient = createWebViewClient(gatewayToken, callbacks)

                // 设置 WebChromeClient
                webChromeClient = createWebChromeClient(callbacks)

                // 加载 URL
                loadUrl(url)
            }

            return KimiClawWebView(webView)
        }

        /**
         * 生成注入 Gateway Token 的 JavaScript 代码
         */
        fun createTokenInjectionScript(token: String): String {
            return """
                (function() {
                    try {
                        const SETTINGS_KEY = '$SETTINGS_KEY';
                        
                        // 读取现有配置
                        let settings = {};
                        const existing = localStorage.getItem(SETTINGS_KEY);
                        if (existing) {
                            settings = JSON.parse(existing);
                        }
                        
                        // 如果 token 已经正确设置，不需要重复注入
                        if (settings.token === '$token') {
                            console.log('[KimiClaw] Token already set, skipping injection');
                            return 'token_already_set';
                        }
                        
                        // 更新 token 和相关配置
                        settings.token = '$token';
                        settings.gatewayUrl = settings.gatewayUrl || 'ws://127.0.0.1:18789';
                        
                        // 保存回 localStorage
                        localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
                        
                        // 同时设置旧的 key 以确保兼容性
                        localStorage.setItem('openclaw-gateway-token', '$token');
                        localStorage.setItem('gateway-token', '$token');
                        
                        console.log('[KimiClaw] Token injected successfully to', SETTINGS_KEY);
                        
                        // 触发 storage 事件通知页面更新
                        window.dispatchEvent(new StorageEvent('storage', {
                            key: SETTINGS_KEY,
                            newValue: JSON.stringify(settings)
                        }));
                        
                        return 'token_injected';
                    } catch (e) {
                        console.error('[KimiClaw] Token injection failed:', e);
                        return 'error: ' + e.message;
                    }
                })();
            """.trimIndent()
        }

        private fun createWebViewClient(
            gatewayToken: String?,
            callbacks: WebViewCallbacks
        ): WebViewClient {
            return object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {
                    Logger.logDebug(LOG_TAG, "Page started loading: $url")
                    callbacks.onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Logger.logDebug(LOG_TAG, "Page finished loading: $url")

                    val canGoBack = view?.canGoBack() ?: false
                    val title = view?.title ?: "OpenClaw Dashboard"

                    // 注入 Gateway Token
                    gatewayToken?.let { token ->
                        val jsCode = createTokenInjectionScript(token)

                        view?.evaluateJavascript(jsCode) { result ->
                            Logger.logDebug(LOG_TAG, "Token injection result: $result")
                            // 只有 token 是新注入的才刷新页面，避免无限循环
                            if (result == "\"token_injected\"") {
                                view.postDelayed({
                                                     view.evaluateJavascript("window.location.reload()", null)
                                                 }, 300)
                            }
                        }
                    } ?: run {
                        Logger.logWarn(LOG_TAG, "No gateway token available for injection")
                    }

                    callbacks.onPageFinished(title, canGoBack)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // 只处理主框架的错误
                    if (request?.isForMainFrame == true) {
                        val errorMessage = when (error?.errorCode) {
                            ERROR_CONNECT -> "无法连接到 OpenClaw Gateway，请确保 Gateway 已启动"
                            ERROR_TIMEOUT -> "连接超时，请检查网络或 Gateway 状态"
                            ERROR_HOST_LOOKUP -> "无法解析地址，请检查网络连接"
                            else -> "加载失败: ${error?.description ?: "未知错误"}"
                        }
                        Logger.logError(LOG_TAG, "Page error: $errorMessage")
                        callbacks.onError(errorMessage)
                    }
                }
            }
        }

        private fun createWebChromeClient(callbacks: WebViewCallbacks): WebChromeClient {
            return object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    title?.let { callbacks.onTitleChanged(it) }
                }

                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        Logger.logDebug(
                            "WebViewConsole",
                            "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                        )
                    }
                    return true
                }
            }
        }
    }

    // ==================== WebView 操作 ====================

    /**
     * 获取 WebView 实例（用于 Compose 的 AndroidView）
     */
    fun getWebView(): WebView = webView

    /**
     * 重新加载页面
     */
    fun reload() {
        Logger.logDebug(LOG_TAG, "Reloading page")
        webView.reload()
    }

    /**
     * 是否可以返回
     */
    fun canGoBack(): Boolean = webView.canGoBack()

    /**
     * 返回上一页
     */
    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    /**
     * 销毁 WebView，释放资源
     */
    fun destroy() {
        Logger.logDebug(LOG_TAG, "Destroying WebView")
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    /**
     * WebView 回调接口
     */
    interface WebViewCallbacks {
        /** 页面开始加载 */
        fun onPageStarted()

        /** 页面加载完成 */
        fun onPageFinished(title: String, canGoBack: Boolean)

        /** 页面标题变化 */
        fun onTitleChanged(title: String)

        /** 页面加载错误 */
        fun onError(errorMessage: String)
    }
}
