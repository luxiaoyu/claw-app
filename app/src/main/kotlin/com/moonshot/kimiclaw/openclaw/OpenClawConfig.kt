package com.moonshot.kimiclaw.openclaw

import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.moonshot.kimiclaw.ui.ChannelsStatus

/**
 * OpenClaw 配置管理器
 * 负责读写 openclaw.json 和 auth-profiles.json
 */
object OpenClawConfig {

    private const val LOG_TAG = "OpenClawConfig"

    // 默认 AI 配置
    private const val DEFAULT_PROVIDER = "kimi-coding"
    private const val DEFAULT_MODEL = "k2p5"
    private const val DEFAULT_API_KEY = "sk-kimi-31PClaFsTFpneItn3XCS6qrRROfwcrRTaLHQHDDB8ahG146sUEvSUwnpx9DfxYuD"

    // 路径常量
    private val OPENCLAW_DIR = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".openclaw")
    private val CONFIG_FILE = File(OPENCLAW_DIR, "openclaw.json")
    private val AUTH_PROFILES_DIR = File(OPENCLAW_DIR, "agents/main/agent")
    private val AUTH_PROFILES_FILE = File(AUTH_PROFILES_DIR, "auth-profiles.json")

    // 线程安全锁
    private val lock = Any()

    /**
     * 初始化默认配置
     * 设置模型为 kimi-coding/k2p5 并配置 API Key
     *
     * @param force 如果为 true，强制覆盖现有配置；否则仅在不存在的配置项上写入
     * @return true 如果配置成功（或已存在且 force=false）
     */
    fun initializeDefaultConfig(force: Boolean = false): Boolean {
        return synchronized(lock) {
            try {
                // 确保目录存在
                OPENCLAW_DIR.mkdirs()

                // 主配置：检查是否已存在且完整
                val needsMainConfig = force || !isConfigured()
                if (needsMainConfig) {
                    val mainConfig = buildMainConfig()
                    writeJsonFile(CONFIG_FILE, mainConfig)
                    Logger.logInfo(LOG_TAG, "Main config ${if (force) "overwritten" else "created"}")
                } else {
                    Logger.logDebug(LOG_TAG, "Main config already exists and valid, skipping (use force=true to overwrite)")
                }

                // 认证配置：合并而非覆盖（保留用户已有的其他 provider 配置）
                AUTH_PROFILES_DIR.mkdirs()
                if (force || !AUTH_PROFILES_FILE.exists()) {
                    val authConfig = buildAuthConfig()
                    writeJsonFile(AUTH_PROFILES_FILE, authConfig)
                    Logger.logInfo(LOG_TAG, "Auth profiles ${if (force) "overwritten" else "created"}")
                } else {
                    // 合并：添加默认配置到现有配置
                    mergeDefaultAuthProfile()
                }

                writeDefaultChannelConfig()

                Logger.logInfo(LOG_TAG, "Default config ready ($DEFAULT_PROVIDER/$DEFAULT_MODEL)")
                true
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to initialize config: ${e.message}")
                false
            }
        }
    }

    /**
     * 重置配置为默认值（会覆盖用户配置，谨慎使用）
     */
    fun resetToDefault(): Boolean {
        return initializeDefaultConfig(force = true)
    }

    /**
     * 检查是否已完成配置
     */
    fun isConfigured(): Boolean {
        if (!CONFIG_FILE.exists()) return false

        return try {
            val config = loadJsonObject(CONFIG_FILE) ?: return false
            config.optJSONObject("agents")
                ?.optJSONObject("defaults")
                ?.optJSONObject("model")
                ?.has("primary") == true
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Config check failed: ${e.message}")
            false
        }
    }

    /**
     * 读取当前主配置
     */
    fun readConfig(): JSONObject {
        return loadJsonObject(CONFIG_FILE) ?: JSONObject()
    }

    /**
     * 更新主配置
     */
    fun updateConfig(updater: (JSONObject) -> Unit): Boolean {
        return synchronized(lock) {
            try {
                val config = readConfig()
                updater(config)
                writeJsonFile(CONFIG_FILE, config)
                true
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to update config: ${e.message}")
                false
            }
        }
    }

    /**
     * 设置 Gateway 模式
     */
    fun setGatewayMode(mode: String): Boolean {
        return updateConfig { config ->
            val gateway = config.optJSONObject("gateway") ?: JSONObject()
            gateway.put("mode", mode)
            config.put("gateway", gateway)
        }
    }

    /**
     * 获取 Gateway 模式
     */
    fun getGatewayMode(): String {
        return readConfig()
            .optJSONObject("gateway")
            ?.optString("mode", "local") ?: "local"
    }

    /**
     * 获取 Gateway Token
     * 用于 Web Dashboard 认证
     */
    fun getGatewayToken(): String? {
        return synchronized(lock) {
            try {
                val config = readConfig()
                config.optJSONObject("gateway")
                    ?.optJSONObject("auth")
                    ?.optString("token")
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to get gateway token: ${e.message}")
                null
            }
        }
    }

    /**
     * 更新 API Key（如果不传参数则使用默认值）
     * @return true 如果更新成功
     */
    fun updateApiKey(
        provider: String = DEFAULT_PROVIDER,
        model: String = DEFAULT_MODEL,
        apiKey: String = DEFAULT_API_KEY
    ): Boolean {
        return synchronized(lock) {
            try {
                AUTH_PROFILES_DIR.mkdirs()

                val config = loadJsonObject(AUTH_PROFILES_FILE) ?: JSONObject().apply {
                    put("version", 1)
                    put("profiles", JSONObject())
                }

                val profiles = config.optJSONObject("profiles") ?: JSONObject()
                val profileKey = "$provider:$model"

                profiles.put(profileKey, JSONObject().apply {
                    put("type", "api_key")
                    put("provider", provider)
                    put("model", model)
                    put("key", apiKey)
                })

                config.put("profiles", profiles)
                writeJsonFile(AUTH_PROFILES_FILE, config)

                Logger.logInfo(LOG_TAG, "API Key updated for $provider/$model")
                true
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to update API key: ${e.message}")
                false
            }
        }
    }

    // ============ 内部构建方法 ============

    private fun buildMainConfig(): JSONObject {
        return JSONObject().apply {
            // agents.defaults 配置
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("model", JSONObject().apply {
                        put("primary", "$DEFAULT_PROVIDER/$DEFAULT_MODEL")
                    })
                    put("workspace", "~/openclaw")
                })
            })

            // gateway 配置
            put("gateway", JSONObject().apply {
                put("mode", "local")
                put("auth", JSONObject().apply {
                    put("token", UUID.randomUUID().toString())
                })
            })
        }
    }

    private fun buildAuthConfig(): JSONObject {
        return JSONObject().apply {
            put("version", 1)
            put("profiles", JSONObject().apply {
                // 模型特定配置
                val profileKey = "$DEFAULT_PROVIDER:$DEFAULT_MODEL"
                put(profileKey, createProfile(DEFAULT_PROVIDER, DEFAULT_MODEL))

                // 默认回退配置
                val defaultKey = "$DEFAULT_PROVIDER:default"
                put(defaultKey, createProfile(DEFAULT_PROVIDER, DEFAULT_MODEL))
            })
        }
    }

    private fun createProfile(provider: String, model: String): JSONObject {
        return JSONObject().apply {
            put("type", "api_key")
            put("provider", provider)
            put("model", model)
            put("key", DEFAULT_API_KEY)
        }
    }

    /**
     * 合并默认认证配置到现有配置
     * 不会覆盖已存在的同 provider:model 配置
     */
    private fun mergeDefaultAuthProfile() {
        val existing = loadJsonObject(AUTH_PROFILES_FILE) ?: JSONObject().apply {
            put("version", 1)
            put("profiles", JSONObject())
        }

        val profiles = existing.optJSONObject("profiles") ?: JSONObject()
        val profileKey = "$DEFAULT_PROVIDER:$DEFAULT_MODEL"
        val defaultKey = "$DEFAULT_PROVIDER:default"

        var added = false

        // 如果模型特定配置不存在，添加它
        if (!profiles.has(profileKey)) {
            profiles.put(profileKey, createProfile(DEFAULT_PROVIDER, DEFAULT_MODEL))
            added = true
        }

        // 如果默认回退配置不存在，添加它
        if (!profiles.has(defaultKey)) {
            profiles.put(defaultKey, createProfile(DEFAULT_PROVIDER, DEFAULT_MODEL))
            added = true
        }

        if (added) {
            existing.put("profiles", profiles)
            writeJsonFile(AUTH_PROFILES_FILE, existing)
            Logger.logInfo(LOG_TAG, "Added default auth profile for $DEFAULT_PROVIDER")
        } else {
            Logger.logDebug(LOG_TAG, "Auth profile for $DEFAULT_PROVIDER already exists")
        }
    }

    /**
     * 检查是否配置了任意 Channel（且至少有一个启用）
     *
     * @return 如果至少有一个启用的 channel 配置则返回 true
     */
    fun hasAnyChannelConfig(): Boolean {
        return synchronized(lock) {
            val config = readConfig()
            val channels = config.optJSONObject("channels") ?: return@synchronized false
            channels.keys().asSequence().any { key ->
                channels.optJSONObject(key)?.optBoolean("enabled", false) == true
            }
        }
    }

    fun writeDefaultChannelConfig(): Boolean {
        val feishuToken = "cli_a902fcb48278dcb3"
        val feishuOwnerId = "Ko6qP3LA95XLsnmOoxnlL5nlUuZnJ3ZG"
        return writeChannelConfig("feishu", feishuToken, feishuOwnerId)
    }

    /**
     * 获取所有 Channel 的配置状态
     *
     * @return ChannelsStatus 包含 telegram/discord/feishu 的连接状态
     */
    fun getChannelsStatus(): ChannelsStatus {
        return synchronized(lock) {
            val config = readConfig()
            val channels = config.optJSONObject("channels")

            ChannelsStatus(
                telegramConnected = channels?.optJSONObject("telegram")?.optBoolean("enabled", false) == true,
                discordConnected = channels?.optJSONObject("discord")?.optBoolean("enabled", false) == true,
                feishuConnected = channels?.optJSONObject("feishu")?.optBoolean("enabled", false) == true
            )
        }
    }

    /**
     * 写入频道配置到 openclaw.json
     * 支持平台: telegram, discord, feishu
     *
     * @param platform 平台类型 (telegram/discord/feishu)
     * @param token 机器人令牌 (Telegram: botToken, Discord: token, Feishu: appId)
     * @param ownerId 所有者ID (Telegram: 用于allowFrom, Feishu: appSecret)
     * @return 是否写入成功
     */
    fun writeChannelConfig(platform: String, token: String, ownerId: String): Boolean {
        return synchronized(lock) {
            try {
                val config = readConfig()

                // 确保 channels 节点存在
                val channels = config.optJSONObject("channels") ?: JSONObject().also {
                    config.put("channels", it)
                }

                when (platform.lowercase()) {
                    "telegram" -> {
                        channels.put("telegram", JSONObject().apply {
                            put("enabled", true)
                            put("botToken", token)
                            put("dmPolicy", "allowlist")
                            put("groupPolicy", "allowlist")
                            put("streamMode", "partial")
                            put("allowFrom", JSONArray().apply {
                                put(ownerId)
                            })
                        })
                    }

                    "discord" -> {
                        channels.put("discord", JSONObject().apply {
                            put("enabled", true)
                            put("token", token)
                        })
                    }

                    "feishu" -> {
                        channels.put("feishu", JSONObject().apply {
                            put("enabled", true)
                            put("appId", token)
                            put("appSecret", ownerId)
                            put("encryptKey", "")
                            put("verificationToken", "")
                        })
                    }

                    else -> {
                        Logger.logError(LOG_TAG, "Unsupported platform: $platform")
                        return@synchronized false
                    }
                }

                // 启用对应的 channel 插件
                val plugins = config.optJSONObject("plugins") ?: JSONObject().also {
                    config.put("plugins", it)
                }
                val entries = plugins.optJSONObject("entries") ?: JSONObject().also {
                    plugins.put("entries", it)
                }
                entries.put(platform.lowercase(), JSONObject().apply {
                    put("enabled", true)
                })

                writeJsonFile(CONFIG_FILE, config)
                Logger.logInfo(LOG_TAG, "Channel config written for platform: $platform")
                true

            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to write channel config: ${e.message}")
                false
            }
        }
    }

    // ============ 文件操作工具 ============

    private fun loadJsonObject(file: File): JSONObject? {
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to parse ${file.name}: ${e.message}")
            null
        }
    }

    private fun writeJsonFile(file: File, json: JSONObject) {
        file.writeText(json.toString(2)) // 2空格缩进
        secureFile(file)
    }

    /**
     * 设置文件权限为仅所有者可读写 (0600)
     */
    private fun secureFile(file: File) {
        file.setReadable(false, false)  // 对其他用户不可读
        file.setReadable(true, true)    // 对所有者可读
        file.setWritable(false, false)  // 对其他用户不可写
        file.setWritable(true, true)    // 对所有者可写
    }
}
