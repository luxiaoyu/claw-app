package com.moonshot.kimiclaw.openclaw

import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Job
import com.moonshot.kimiclaw.KimiClawService

/**
 * OpenClaw 辅助工具类
 * 提供网关控制等操作
 */
object OpenClawHelper {

    private const val LOG_TAG = "OpenClawHelper"
    private const val SCRIPTS_DIR = "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/share/kimiclaw"

    private val PID_FILE = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.openclaw/gateway.pid"
    private val LOG_FILE = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.openclaw/gateway.log"

    /**
     * 检查 Gateway 是否正在运行
     *
     * @param service KimiClawService 实例，用于执行命令（如果为 null，直接返回 stopped）
     * @param onResult 回调结果，返回是否运行（在主线程执行）
     */
    fun isGatewayRunning(service: KimiClawService?, onResult: (running: Boolean, output: String) -> Unit) {
        // Service 不可用时直接返回 stopped
        if (service == null) {
            Logger.logWarn(LOG_TAG, "KimiClawService not available, returning stopped")
            onResult(false, "stopped")
            return
        }

        val command = buildString {
            appendLine("if [ -f \"$PID_FILE\" ] && kill -0 \$(cat \"$PID_FILE\") 2>/dev/null; then")
            appendLine("  echo running")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("# Check for openclaw gateway process")
            appendLine("if pgrep -f \"openclaw.*gateway\" >/dev/null 2>&1; then")
            appendLine("  echo running")
            appendLine("else")
            appendLine("  echo stopped")
            appendLine("fi")
        }

        // 使用 KimiClawService 执行命令，统一管理并支持取消
        service.executeCommand(
            command = command,
            timeoutMs = 5_000L,
            maxLogLines = 0,
            callback = { result ->
                val output = result.stdout.trim()
                val running = output == "running"
                onResult(running, output)
            }
        )
    }

    /**
     * 获取 Gateway 运行时间
     *
     * @param service KimiClawService 实例，用于执行命令（如果为 null，直接返回 "—"）
     * @param onResult 回调结果，返回 uptime 字符串（在主线程执行）
     */
    fun getGatewayUptime(service: KimiClawService?, onResult: (uptime: String) -> Unit) {
        // Service 不可用时直接返回 "—"
        if (service == null) {
            Logger.logWarn(LOG_TAG, "KimiClawService not available, returning '—'")
            onResult("—")
            return
        }

        val command = buildString {
            appendLine("if [ -f \"$PID_FILE\" ]; then")
            appendLine("  pid=\$(cat \"$PID_FILE\")")
            appendLine("  if kill -0 \$pid 2>/dev/null; then")
            appendLine("    ps -p \$pid -o etime= 2>/dev/null || echo '—'")
            appendLine("  else echo '—'; fi")
            appendLine("else echo '—'; fi")
        }

        // 使用 KimiClawService 执行命令，统一管理并支持取消
        service.executeCommand(
            command = command,
            timeoutMs = 5_000L,
            maxLogLines = 0,
            callback = { result ->
                onResult(result.stdout.trim())
            }
        )
    }

    /**
     * 检查 OpenClaw 是否已安装
     * 检查 openclaw 二进制文件是否存在且可执行
     */
    fun isOpenclawInstalled(): Boolean {
        val binary = File("${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/openclaw")
        return binary.exists() && binary.canExecute()
    }

    /**
     * 检查 Node.js 是否已安装（Bootstrap 检查）
     */
    fun isNodeInstalled(): Boolean {
        return File("${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/node").exists()
    }

    /**
     * 检查 OpenClaw 配置是否存在
     */
    fun isOpenclawConfigured(): Boolean {
        return File("${TermuxConstants.TERMUX_HOME_DIR_PATH}/.openclaw/openclaw.json").exists()
    }

    /**
     * 启动 Gateway
     *
     * @param context Context
     * @param service KimiClawService 实例
     * @param onStart 启动时回调（用于设置状态）
     * @param onResult 结果回调，返回 (success, errorMsg)
     * @return Job 可用于取消执行
     */
    fun startGateway(
        context: Context,
        service: KimiClawService,
        onStart: () -> Unit,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ): Job {
        // 1. 确保配置存在（带超时保护）
        val configReady = try {
            if (OpenClawConfig.isConfigured()) {
                Logger.logDebug(LOG_TAG, "OpenClaw config already exists")
                true
            } else {
                Logger.logInfo(LOG_TAG, "Initializing default OpenClaw config...")
                OpenClawConfig.initializeDefaultConfig()
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Config initialization failed: ${e.message}")
            onResult(false, "配置初始化失败: ${e.message}")
            return Job().apply { complete() }
        }

        if (!configReady) {
            Logger.logError(LOG_TAG, "Failed to initialize OpenClaw config")
            onResult(false, "配置初始化失败")
            return Job().apply { complete() }
        }

        val logDir = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.openclaw"
        val debugLog = "$logDir/gateway-debug.log"
        val home = TermuxConstants.TERMUX_HOME_DIR_PATH
        val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH

        val cmd = buildString {
            appendLine("mkdir -p $logDir")
            appendLine("exec 2>$debugLog")
            appendLine("set -x")
            appendLine("echo \"date: \$(date)\" >&2")
            appendLine("echo \"id: \$(id)\" >&2")
            appendLine("echo \"PATH=\$PATH\" >&2")
            appendLine("pgrep -x sshd || sshd || true")
            appendLine("pkill -f \"gateway.js\" 2>/dev/null || true")
            appendLine("pkill -f \"openclaw.*gateway\" 2>/dev/null || true")
            appendLine("if [ -f $PID_FILE ]; then")
            appendLine("  kill \$(cat $PID_FILE) 2>/dev/null")
            appendLine("  rm -f $PID_FILE")
            appendLine("  sleep 1")
            appendLine("fi")
            appendLine("sleep 1")
            appendLine("echo '' > $LOG_FILE")
            appendLine("export HOME=$home")
            appendLine("export PREFIX=$prefix")
            appendLine("export PATH=\$PREFIX/bin:\$PATH")
            appendLine("export TMPDIR=\$PREFIX/tmp")
            appendLine("export SSL_CERT_FILE=\$PREFIX/etc/tls/cert.pem")
            appendLine("export NODE_OPTIONS=--dns-result-order=ipv4first")
            appendLine("echo \"=== Environment before chroot ===\" >&2")
            appendLine("echo \"SSL_CERT_FILE=\$SSL_CERT_FILE\" >&2")
            appendLine("echo \"NODE_OPTIONS=\$NODE_OPTIONS\" >&2")
            appendLine("echo \"Testing cert file access:\" >&2")
            appendLine("ls -lh \$PREFIX/etc/tls/cert.pem >&2 || echo \"cert.pem not found!\" >&2")
            appendLine("(")
            appendLine("  exec 2>/dev/null")
            appendLine("  cd \$HOME/.openclaw")
            appendLine("  nohup \$PREFIX/bin/openclaw gateway run --force >> $LOG_FILE 2>&1 &")
            appendLine("  GW_PID=\$!")
            appendLine("  sleep 0.5")
            appendLine("  ACTUAL_PID=\$(pgrep -f \"openclaw.*gateway\" | head -1)")
            appendLine("  if [ -n \"\$ACTUAL_PID\" ]; then")
            appendLine("    echo \$ACTUAL_PID > $PID_FILE")
            appendLine("    echo \"gateway started with pid: \$ACTUAL_PID\" >&2")
            appendLine("  else")
            appendLine("    echo \$GW_PID > $PID_FILE")
            appendLine("    echo \"gateway started with pid: \$GW_PID (fallback)\" >&2")
            appendLine("  fi")
            appendLine(")")
            appendLine("sleep 3")
            appendLine("PID=\$(cat $PID_FILE 2>/dev/null)")
            appendLine("if [ -n \"\$PID\" ] && kill -0 \$PID 2>/dev/null; then")
            appendLine("  echo started")
            appendLine("elif pgrep -f \"openclaw.*gateway\" >/dev/null 2>&1; then")
            appendLine("  pgrep -f \"openclaw.*gateway\" | head -1 > $PID_FILE")
            appendLine("  echo started")
            appendLine("else")
            appendLine("  echo \"gateway died, log:\" >&2")
            appendLine("  cat $LOG_FILE >&2")
            appendLine("  rm -f $PID_FILE")
            appendLine("  cat $LOG_FILE")
            appendLine("  echo '---'")
            appendLine("  cat $debugLog")
            appendLine("  exit 1")
            appendLine("fi")
        }

        Logger.logInfo(LOG_TAG, "Starting gateway")
        onStart()

        // 使用 KimiClawService 执行命令，返回 Job 以便取消
        return service.executeCommand(cmd, timeoutMs = 60_000L) { result ->
            val success = result.success && result.stdout.contains("started")
            val errorMsg = if (!success) {
                "Exit code: ${result.exitCode}, stderr: ${result.stderr}"
            } else null

            Logger.logInfo(LOG_TAG, "Gateway start result: $success")
            onResult(success, errorMsg)
        }
    }

    /**
     * 停止 Gateway
     *
     * @param service KimiClawService 实例
     * @param onStart 停止时回调（用于设置状态）
     * @param onResult 结果回调，返回 (success, errorMsg)
     * @return Job 可用于取消执行
     */
    fun stopGateway(
        service: KimiClawService,
        onStart: () -> Unit,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ): Job {
        val cmd = buildString {
            appendLine("PID=''")
            appendLine("if [ -f $PID_FILE ]; then PID=\$(cat $PID_FILE 2>/dev/null || true); fi")
            appendLine("rm -f $PID_FILE")
            appendLine("if [ -n \"\$PID\" ]; then")
            appendLine("  kill \"\$PID\" 2>/dev/null || true")
            appendLine("  pkill -TERM -P \"\$PID\" 2>/dev/null || true")
            appendLine("fi")
            appendLine("pkill -TERM -f \"openclaw.*gateway\" 2>/dev/null || true")
            appendLine("sleep 1")
            appendLine("pkill -9 -f \"openclaw.*gateway\" 2>/dev/null || true")
            appendLine("echo stopped")
        }

        Logger.logInfo(LOG_TAG, "Stopping gateway")
        onStart()

        // 使用 KimiClawService 执行命令，返回 Job 以便取消
        return service.executeCommand(cmd, timeoutMs = 30_000L) { result ->
            val success = result.success && result.stdout.contains("stopped")
            val errorMsg = if (!success) {
                "Exit code: ${result.exitCode}, stderr: ${result.stderr}"
            } else null

            Logger.logInfo(LOG_TAG, "Gateway stop result: $success")
            onResult(success, errorMsg)
        }
    }

    /**
     * 确保脚本文件存在且可执行
     * 如果不存在，从 assets 复制；如果存在但不可执行，设置可执行权限
     *
     * @param context Context
     * @param scriptName 脚本文件名
     */
    private fun ensureScriptExists(context: Context, scriptName: String): Boolean {
        val scriptFile = File("$SCRIPTS_DIR/$scriptName")

        // 文件已存在，检查是否可执行
        if (scriptFile.exists()) {
            if (!scriptFile.canExecute()) {
                val success = scriptFile.setExecutable(true)
                if (!success) {
                    Logger.logWarn(LOG_TAG, "Failed to set executable permission for existing script: $scriptName")
                }
                return success
            }
            return true
        }

        // 文件不存在，从 assets 复制
        return try {
            // 创建父目录
            val parentDir = scriptFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val mkdirSuccess = parentDir.mkdirs()
                if (!mkdirSuccess) {
                    Logger.logError(LOG_TAG, "Failed to create directory: ${parentDir.absolutePath}")
                    return false
                }
            }

            // 从 assets 复制文件
            context.assets.open(scriptName).use { input ->
                FileOutputStream(scriptFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 设置可执行权限
            val executableSuccess = scriptFile.setExecutable(true)
            if (!executableSuccess) {
                Logger.logError(LOG_TAG, "Failed to set executable permission for: $scriptName")
                return false
            }

            Logger.logInfo(LOG_TAG, "Gateway script copied and made executable: $scriptName")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to copy gateway script '$scriptName': ${e.message}")
            false
        }
    }

}
