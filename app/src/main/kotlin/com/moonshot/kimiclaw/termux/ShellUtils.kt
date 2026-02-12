package com.moonshot.kimiclaw.termux

import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Shell 工具类
 * 提供执行 shell 脚本的通用方法
 */
object ShellUtils {

    private const val LOG_TAG = "ShellUtils"
    const val DEFAULT_TIMEOUT_MS = 60_000L
    const val DEFAULT_MAX_LOG_LINES = 200

    /**
     * 命令执行结果
     */
    data class ShellResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    /**
     * 命令执行状态（用于流式输出）
     */
    sealed class ShellState {
        data class Output(val line: String, val isError: Boolean = false) : ShellState()
        data class Success(val exitCode: Int, val stdout: String, val stderr: String) : ShellState()
        data class Error(val message: String, val cause: Throwable? = null) : ShellState()
        data object Timeout : ShellState()
    }

    /**
     * 默认的 Termux 环境变量配置
     */
    data class TermuxEnvConfig(
        val prefix: String = TermuxConstants.TERMUX_PREFIX_DIR_PATH,
        val home: String = TermuxConstants.TERMUX_HOME_DIR_PATH,
        val tmpDir: String = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH,
        val additionalPaths: List<String> = emptyList(),
        val additionalEnvVars: Map<String, String> = emptyMap(),
        val varsToRemove: Set<String> = emptySet()
    )

    /**
     * 执行 shell 脚本文件
     *
     * @param scriptPath 脚本文件的完整路径
     * @param envConfig 环境变量配置，默认为标准 Termux 环境
     * @param redirectErrorStream 是否将错误流重定向到标准输出流
     * @return 启动的 Process 实例
     */
    @JvmStatic
    @JvmOverloads
    fun executeScript(
        scriptPath: String,
        envConfig: TermuxEnvConfig = TermuxEnvConfig(),
        redirectErrorStream: Boolean = true
    ): Process {
        return ProcessBuilder(
            "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash",
            scriptPath
        ).apply {
            environment().apply {
                setupTermuxEnvironment(envConfig)
            }
            this.redirectErrorStream(redirectErrorStream)
        }.start()
    }

    /**
     * 执行 shell 命令字符串（同步阻塞方式）
     *
     * 实现特点：
     * 1. 通过临时脚本文件执行（避免 bash -c 的可靠性问题）
     * 2. 合并 stderr 到 stdout（避免流阻塞导致的死锁）
     * 3. 超时保护（防止无限挂起）
     * 4. 自动清理临时文件
     * 5. 可控的日志输出
     *
     * @param command 要执行的命令字符串
     * @param envConfig 环境变量配置
     * @param timeoutMs 超时时间（毫秒），默认 60 秒
     * @param maxLogLines 最大日志输出行数，null 表示无限制
     * @return CommandResult 执行结果
     */
    @JvmStatic
    @JvmOverloads
    suspend fun executeCommand(
        command: String,
        envConfig: TermuxEnvConfig = createStandardTermuxEnvConfig(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxLogLines: Int? = DEFAULT_MAX_LOG_LINES
    ): ShellResult {
        Logger.logDebug(LOG_TAG, "Executing command: ${command.take(100)}${if (command.length > 100) "..." else ""}")

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var process: Process? = null

        // 创建临时脚本文件
        val tmpScript = createTempScript(command, envConfig.tmpDir)
            ?: return ShellResult(false, "", "Failed to create temp script", -1)

        try {
            // 构建进程
            val pb = ProcessBuilder(
                "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash",
                tmpScript.absolutePath
            ).apply {
                environment().apply {
                    setupTermuxEnvironment(envConfig)
                    // 额外设置 NODE_OPTIONS 避免 IPv6 延迟
                    put("NODE_OPTIONS", "--dns-result-order=ipv4first")
                }
                // 合并错误流，避免单独消费 stderr 导致的死锁
                redirectErrorStream(true)
            }

            process = pb.start()

            // 读取输出（必须在 waitFor 之前消费完流，避免缓冲区满导致死锁）
            var loggedLines = 0
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    stdout.appendLine(line)

                    // 控制日志输出量
                    if (maxLogLines == null || loggedLines < maxLogLines) {
                        Logger.logVerbose(LOG_TAG, "output: $line")
                        loggedLines++
                    } else if (loggedLines == maxLogLines) {
                        Logger.logVerbose(LOG_TAG, "...(output truncated)")
                        loggedLines++
                    }
                }
            }

            // 等待进程完成（带超时）
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                Logger.logError(LOG_TAG, "Command timeout after ${timeoutMs}ms")
                return ShellResult(false, stdout.toString(), "Command timeout after ${timeoutMs}ms", -1)
            }

            val exitCode = process.exitValue()
            Logger.logDebug(LOG_TAG, "Command exited with code: $exitCode")

            return ShellResult(
                success = exitCode == 0,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                exitCode = exitCode
            )

        } catch (e: IOException) {
            Logger.logError(LOG_TAG, "Command execution IO error: ${e.message}")
            return ShellResult(false, stdout.toString(), "IO Error: ${e.message}", -1)
        } catch (e: InterruptedException) {
            Logger.logError(LOG_TAG, "Command execution interrupted: ${e.message}")
            Thread.currentThread().interrupt()
            return ShellResult(false, stdout.toString(), "Interrupted: ${e.message}", -1)
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Command execution failed: ${e.message}")
            return ShellResult(false, stdout.toString(), "Error: ${e.message}", -1)
        } finally {
            // 仅删除临时脚本，不操作进程
            tmpScript.delete()
        }
    }

    /**
     * 创建临时脚本文件
     */
    private fun createTempScript(command: String, tmpDir: String): File? {
        return try {
            val tmpDirFile = File(tmpDir)
            if (!tmpDirFile.exists()) {
                tmpDirFile.mkdirs()
            }

            File(tmpDirFile, "cmd_${System.currentTimeMillis()}_${Thread.currentThread().id}.sh").apply {
                writeText("""#!/data/data/com.termux/files/usr/bin/bash
                    |$command
                """.trimMargin())
                setExecutable(true)
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to create temp script: ${e.message}")
            null
        }
    }

    /**
     * 设置 Termux 环境变量
     */
    private fun MutableMap<String, String>.setupTermuxEnvironment(config: TermuxEnvConfig) {
        // 清除可能干扰的证书环境变量
        remove("SSL_CERT_FILE")
        remove("SSL_CERT_DIR")
        remove("CURL_CA_BUNDLE")
        remove("REQUESTS_CA_BUNDLE")
        remove("NODE_EXTRA_CA_CERTS")

        // 设置基本路径
        put("PREFIX", config.prefix)
        put("HOME", config.home)
        put("TMPDIR", config.tmpDir)

        // 构建 PATH（Termux bin 优先）
        val pathBuilder = mutableListOf<String>()
        pathBuilder.add(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH)
        pathBuilder.addAll(config.additionalPaths)
        System.getenv("PATH")?.let { pathBuilder.add(it) }
        put("PATH", pathBuilder.joinToString(":"))

        // 设置库路径
        put("LD_LIBRARY_PATH", "${config.prefix}/lib")

        // 设置 SSL 证书路径
        put("SSL_CERT_FILE", "${config.prefix}/etc/tls/cert.pem")

        // 移除 SSH 相关变量（强制 git 使用 HTTPS）
        remove("GIT_SSH")
        remove("GIT_SSH_COMMAND")

        // 应用自定义配置
        config.varsToRemove.forEach { remove(it) }
        config.additionalEnvVars.forEach { (key, value) -> put(key, value) }
    }

    /**
     * 创建标准 Termux 环境配置
     * 包含 git-core 路径的标准配置
     */
    @JvmStatic
    fun createStandardTermuxEnvConfig(): TermuxEnvConfig {
        return TermuxEnvConfig(
            additionalPaths = listOf(
                "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/libexec/git-core"
            )
        )
    }

    /**
     * 检查命令是否存在
     */
    @JvmStatic
    suspend fun commandExists(command: String): Boolean {
        return executeCommand("which $command > /dev/null 2>&1").success
    }

    /**
     * 获取命令路径
     */
    @JvmStatic
    suspend fun getCommandPath(command: String): String? {
        val result = executeCommand("which $command", maxLogLines = 0)
        return if (result.success) result.stdout.trim() else null
    }

    /**
     * SSH 连接信息数据类
     */
    data class SshConnectionInfo(
        val host: String,
        val port: Int = 8022,
        val password: String? = null
    ) {
        val connectionString: String
            get() = "ssh -p $port $host"

        val displayText: String
            get() = buildString {
                appendLine(connectionString)
                append("Password: ${password ?: "<not set>"}")
            }
    }

    /**
     * 获取设备网络信息
     * 遍历所有网络接口，返回第一个可用的 IPv4 地址
     */
    @JvmStatic
    fun getDeviceNetworkInfo(): SshConnectionInfo {
        val ip = discoverLocalIp() ?: "<device-ip>"
        val password = fetchSshPassword()
        return SshConnectionInfo(host = ip, password = password)
    }

    /**
     * 发现本地网络中的 IPv4 地址
     */
    private fun discoverLocalIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Network discovery failed: ${e.message}")
            null
        }
    }

    /**
     * 从文件获取 SSH 认证密码
     */
    private fun fetchSshPassword(): String? {
        val credentialFile = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".ssh_password")

        return credentialFile.takeIf { it.exists() }
            ?.bufferedReader()
            ?.use { reader ->
                reader.readLine()
                    ?.takeIf { it.isNotBlank() }
                    ?.trim()
            }
            .also { password ->
                if (password == null) {
                    Logger.logDebug(LOG_TAG, "No SSH password found in ${credentialFile.path}")
                }
            }
    }
}
