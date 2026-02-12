package com.termux.app

import android.app.Activity
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Termux 环境设置管理器
 * 负责检查并安装 Termux bootstrap
 */
object TermuxSetup {

    private const val LOG_TAG = "TermuxSetup"

    /**
     * 检查 bootstrap 是否已安装
     * 通过检查 $PREFIX 目录是否存在且非空
     */
    @JvmStatic
    fun isBootstrapInstalled(): Boolean {
        val prefixDir = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)

        // 检查目录是否存在
        if (!prefixDir.exists()) {
            Logger.logInfo(LOG_TAG, "Bootstrap not installed: ${TermuxConstants.TERMUX_PREFIX_DIR_PATH} does not exist")
            return false
        }

        // 检查目录是否是目录
        if (!prefixDir.isDirectory) {
            Logger.logError(LOG_TAG, "Bootstrap check failed: ${TermuxConstants.TERMUX_PREFIX_DIR_PATH} is not a directory")
            return false
        }

        // 检查目录是否为空（至少要有 bin 目录才算安装完成）
        val binDir = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH)
        if (!binDir.exists() || !binDir.isDirectory) {
            Logger.logInfo(LOG_TAG, "Bootstrap not fully installed: bin directory missing")
            return false
        }

        // 检查关键文件是否存在（bash）
        val bashFile = File("${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash")
        if (!bashFile.exists()) {
            Logger.logInfo(LOG_TAG, "Bootstrap not fully installed: bash binary missing")
            return false
        }

        Logger.logInfo(LOG_TAG, "Bootstrap is installed at ${TermuxConstants.TERMUX_PREFIX_DIR_PATH}")
        return true
    }

    /**
     * 设置 bootstrap（如果需要）
     *
     * @param activity 当前 Activity
     * @param onComplete 安装完成后的回调（无论是否实际需要安装都会调用）
     * @param onStatusUpdate 状态更新回调，用于更新 UI 提示
     */
    @JvmStatic
    fun setupBootstrapIfNeeded(
        activity: Activity,
        onComplete: () -> Unit,
        onStatusUpdate: ((String) -> Unit)? = null
    ) {
        if (isBootstrapInstalled()) {
            Logger.logInfo(LOG_TAG, "Bootstrap already installed, skipping setup")
            onComplete()
            return
        }

        Logger.logInfo(LOG_TAG, "Bootstrap not ready, waiting for TermuxInstaller")
        onStatusUpdate?.invoke("Setting up environment...")

        // 调用 TermuxInstaller 设置 bootstrap（package-private，需要在同包内）
        TermuxInstaller.setupBootstrapIfNeeded(activity) {
            // 安装完成后的回调
            Logger.logInfo(LOG_TAG, "Bootstrap setup completed")
            onComplete()
        }
    }

    /**
     * 获取 bootstrap 状态信息（用于调试）
     */
    @JvmStatic
    fun getBootstrapStatus(): String {
        val prefixDir = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        val binDir = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH)
        val bashFile = File("${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash")

        return buildString {
            appendLine("Bootstrap Status:")
            appendLine("  PREFIX path: ${TermuxConstants.TERMUX_PREFIX_DIR_PATH}")
            appendLine("  PREFIX exists: ${prefixDir.exists()}")
            appendLine("  PREFIX is directory: ${prefixDir.isDirectory}")
            appendLine("  BIN path: ${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}")
            appendLine("  BIN exists: ${binDir.exists()}")
            appendLine("  Bash exists: ${bashFile.exists()}")
            appendLine("  Installed: ${isBootstrapInstalled()}")
        }
    }
}
