package com.moonshot.kimiclaw

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.termux.shared.logger.Logger

/**
 * Phantom Process Killing 检测与引导助手
 * 
 * Android 12+ 系统限制后台子进程数（默认32个），会导致 Termux/KimiClaw 被杀死
 * 
 * 本助手仅提供无需电脑、无需 Root 的解决方案：
 * - Android 14+: 开发者选项中禁用子进程限制
 * - Android 12/13: 引导用户到电池优化设置，减少被杀概率
 */
object PhantomProcessHelper {

    private const val LOG_TAG = "PhantomProcessHelper"

    /**
     * Phantom Process 状态
     */
    data class PhantomProcessStatus(
        val isAndroid12Plus: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        val canUseDeveloperOption: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE, // Android 14+
        val isBatteryOptimizationDisabled: Boolean? = null
    ) {
        val isProblematic: Boolean
            get() = isAndroid12Plus && !canUseDeveloperOption
    }

    /**
     * 获取当前状态信息
     */
    fun getStatus(): PhantomProcessStatus {
        return PhantomProcessStatus()
    }

    /**
     * 打开开发者选项设置（Android 14+）
     * 用户需要手动找到 "Disable child process restrictions" 并开启
     */
    fun openDeveloperOptions(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to open developer options: ${e.message}")
            false
        }
    }

    /**
     * 打开电池优化设置
     * 引导用户将 KimiClaw 设置为"不优化"
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to open battery settings: ${e.message}")
            false
        }
    }

    /**
     * 获取帮助说明（仅保留手机端可操作的方法）
     */
    fun getHelpText(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+
                """### Android 14+ 解决方案

**操作步骤：**

1. 打开 **设置 → 关于手机**
2. 连续点击 **版本号** 7 次，开启开发者选项
3. 返回 **设置 → 系统 → 开发者选项**
4. 找到 **"Disable child process restrictions"**（禁用子进程限制）
5. **开启**该选项
6. **重启手机**

⚠️ **注意：** 如果关闭开发者选项，此设置会失效，建议保持开发者选项开启

---

**额外建议：**
- 将 KimiClaw 设为"电池不优化"，减少后台被杀概率
- 开启后，KimiClaw 可在后台稳定运行，不会被系统限制进程数"""
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12/13
                """### Android 12/13 缓解方案

由于系统限制，Android 12/13 无法完全禁用 Phantom Process Killing，但可以缓解：

**1. 忽略电池优化**
- 设置 → 应用 → KimiClaw → 电池 → 不优化
- 允许后台高耗电

**2. 保持 APP 在前台**
- 运行 Gateway 时，保持 KimiClaw 在前台或最近任务中
- 不要划掉 APP

**3. 使用通知保持存活**
- KimiClaw 已以前台服务运行，带有持久通知
- 不要关闭此通知

**4. 被杀后自动重启**
- 开启系统自启动权限（如果有）
- KimiClaw 会在被杀后尝试恢复

---

⚠️ **彻底解决方案：**
如需完全解决，建议升级到 Android 14+，或在开发者选项中查看是否有"Disable child process restrictions"选项（部分厂商系统已提前加入）"""
            }
            else -> {
                // Android 12 以下
                """您的设备运行的是 Android 12 以下版本，不受 Phantom Process Killing 影响。

如遇到后台被杀问题，建议：
- 将 KimiClaw 设为电池不优化
- 允许自启动和后台运行"""
            }
        }
    }
}
