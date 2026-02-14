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
         * @param gatewayToken Gateway Token 用于自动认证，将作为 URL 参数传递
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
            
            // 构建带 token 的 URL
            val finalUrl = buildUrlWithToken(url, gatewayToken)

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

                // 加载 URL（带 token 参数）
                loadUrl(finalUrl)
            }

            return KimiClawWebView(webView)
        }

        /**
         * 构建带 token 参数的 URL
         * 
         * @param baseUrl 基础 URL
         * @param token Gateway Token
         * @return 带 token 参数的 URL
         */
        fun buildUrlWithToken(baseUrl: String, token: String?): String {
            if (token.isNullOrEmpty()) {
                return baseUrl
            }
            
            return try {
                val separator = if (baseUrl.contains("?")) "&" else "?"
                "${baseUrl}${separator}token=${token}"
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to build URL with token: ${e.message}")
                baseUrl
            }
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

                    // Token 已通过 URL 参数传递，无需 JavaScript 注入
                    Logger.logDebug(LOG_TAG, "Page loaded with token in URL parameter")

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
