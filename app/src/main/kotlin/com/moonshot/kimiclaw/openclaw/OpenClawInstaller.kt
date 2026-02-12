package com.moonshot.kimiclaw.openclaw

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.timeout

/**
 * OpenClaw 安装器
 * 执行 install.sh 脚本并解析结构化输出
 */
object OpenClawInstaller {

    private const val LOG_TAG = "OpenClawInstaller"
    private const val INSTALL_SCRIPT = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/share/kimiclaw/install.sh"
    private const val MAX_RECENT_LINES = 20
    private val TIMEOUT_MINUTES = 5.minutes

    /**
     * 安装状态密封类
     */
    sealed class InstallState {
        data class Log(val line: String) : InstallState()  // 实时日志输出
        data class Error(val message: String) : InstallState()
        data object Complete : InstallState()
        data object AlreadyInstalled : InstallState()
    }

    /**
     * 执行 OpenClaw 安装
     * @param context Context 用于访问 assets
     * @return Flow<InstallState> 安装状态流
     */
    @OptIn(FlowPreview::class)
    fun install(context: Context): Flow<InstallState> = flow {
        Logger.logInfo(LOG_TAG, "Starting OpenClaw installation...")
        Logger.logInfo(LOG_TAG, "Install script path: $INSTALL_SCRIPT")
        
        // 每次强制从 assets 复制 install.sh，确保使用最新版本
        Logger.logInfo(LOG_TAG, "Copying install script from assets...")
        if (!copyScriptFromAssets(context)) {
            Logger.logError(LOG_TAG, "Failed to copy install script from assets")
            emit(InstallState.Error("Failed to copy install script from assets"))
            return@flow
        }
        
        Logger.logInfo(LOG_TAG, "Install script ready, starting process...")

        var process: Process? = null
        val recentLines = ArrayDeque<String>(MAX_RECENT_LINES)

        try {
            process = startInstallProcess()
            Logger.logInfo(LOG_TAG, "Starting install via $INSTALL_SCRIPT")

            // 读取并解析输出
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    Logger.logVerbose(LOG_TAG, "install.sh: $line")

                    recentLines.addLast(line)
                    if (recentLines.size > MAX_RECENT_LINES) {
                        recentLines.removeFirst()
                    }

                    emit(InstallState.Log(line))
                    parseSpecialLine(line)?.let { emit(it) }
                }
            }

            // 等待进程完成
            val finished = process.waitFor(TIMEOUT_MINUTES.inWholeMinutes, TimeUnit.MINUTES)
            if (!finished) {
                throw InstallException.Timeout("Installation timed out after ${TIMEOUT_MINUTES.inWholeMinutes} minutes")
            }

            // 处理退出码
            if (process.exitValue() != 0) {
                val tail = recentLines.joinToString("\n")
                emit(InstallState.Error("Installation failed (exit code ${process.exitValue()})\n\n$tail"))
            }

        } catch (e: CancellationException) {
            process?.destroyForcibly()
            Logger.logInfo(LOG_TAG, "Installation cancelled")
            throw e
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Installation failed: ${e.message}")
            emit(InstallState.Error("Installation error: ${e.message}"))
        } finally {
            process?.apply {
                if (isAlive) {
                    destroyForcibly()
                    Logger.logWarn(LOG_TAG, "Process was still alive, forcibly destroyed")
                }
            }
        }
    }.flowOn(Dispatchers.IO).timeout(TIMEOUT_MINUTES + 1.minutes)

    /**
     * 验证安装脚本是否存在
     */
    private fun verifyScriptExists(): Boolean = File(INSTALL_SCRIPT).exists()

    /**
     * 从 assets 复制安装脚本
     */
    private fun copyScriptFromAssets(context: Context): Boolean {
        return try {
            val targetFile = File(INSTALL_SCRIPT)
            targetFile.parentFile?.mkdirs()
            
            context.assets.open("install.sh").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // 设置可执行权限
            targetFile.setExecutable(true)
            
            Logger.logInfo(LOG_TAG, "Install script copied from assets to $INSTALL_SCRIPT")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to copy install script: ${e.message}")
            false
        }
    }

    /**
     * 启动安装进程
     */
    private fun startInstallProcess(): Process {
        return ProcessBuilder(
            "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash",
            INSTALL_SCRIPT
        ).apply {
            environment().apply {
                // Clear potentially inherited wrong paths
                remove("SSL_CERT_FILE")
                remove("SSL_CERT_DIR")
                remove("CURL_CA_BUNDLE")
                remove("REQUESTS_CA_BUNDLE")
                remove("NODE_EXTRA_CA_CERTS")
                
                put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
                put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH)
                // Include libexec/git-core for git-remote-https and other helpers
                put("PATH", "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/libexec/git-core:${System.getenv("PATH")}")
                put("LD_LIBRARY_PATH", "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/lib")
                put("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH)
                // Set correct SSL cert path
                put("SSL_CERT_FILE", "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/etc/tls/cert.pem")
                // Remove SSH-related env vars to force git use HTTPS
                remove("GIT_SSH")
                remove("GIT_SSH_COMMAND")
            }
            redirectErrorStream(true)
        }.start()
    }

    /**
     * 解析特殊状态行
     */
    private fun parseSpecialLine(line: String): InstallState? = when {
        line == "KIMICLAW_COMPLETE" -> InstallState.Complete
        line == "KIMICLAW_ALREADY_INSTALLED" -> InstallState.AlreadyInstalled
        line.startsWith("KIMICLAW_ERROR:") -> InstallState.Error(line.removePrefix("KIMICLAW_ERROR:"))
        else -> null
    }

    /**
     * 安装异常
     */
    sealed class InstallException(message: String) : Exception(message) {
        class Timeout(message: String) : InstallException(message)
    }
}

/**
 * 便捷的挂起函数扩展
 */
suspend fun OpenClawInstaller.installSuspend(
    context: Context,
    onLog: (line: String) -> Unit = { },
    onError: (message: String) -> Unit = { },
    onComplete: () -> Unit = { }
) {
    install(context).collect { state ->
        when (state) {
            is OpenClawInstaller.InstallState.Log -> onLog(state.line)
            is OpenClawInstaller.InstallState.Error -> onError(state.message)
            OpenClawInstaller.InstallState.Complete,
            OpenClawInstaller.InstallState.AlreadyInstalled -> onComplete()
        }
    }
}
