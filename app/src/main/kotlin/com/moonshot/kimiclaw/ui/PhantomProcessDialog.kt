package com.moonshot.kimiclaw.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moonshot.kimiclaw.PhantomProcessHelper
import com.moonshot.kimiclaw.ui.theme.lightBrandNormal
import com.moonshot.kimiclaw.ui.theme.lightBubbleSurface
import com.moonshot.kimiclaw.ui.theme.lightSuccess
import com.moonshot.kimiclaw.ui.theme.lightSurface06
import com.moonshot.kimiclaw.ui.theme.lightTextCaption
import com.moonshot.kimiclaw.ui.theme.lightTextPrimary
import com.moonshot.kimiclaw.ui.theme.lightTextSecondary

/**
 * Phantom Process Killing 说明对话框
 * 
 * 仅提供无需电脑、无需 Root 的解决方案：
 * - Android 14+: 引导用户到开发者选项
 * - Android 12/13: 提供缓解方案
 */
@Composable
fun PhantomProcessDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val status = remember { PhantomProcessHelper.getStatus() }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = lightBubbleSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (status.canUseDeveloperOption) 
                            Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (status.canUseDeveloperOption) lightSuccess else lightBrandNormal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "后台进程保护",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = lightTextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 状态说明
                StatusCard(status = status)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 解决方案
                if (status.canUseDeveloperOption) {
                    // Android 14+: 提供开发者选项入口
                    Android14Solution(
                        onOpenDeveloperOptions = {
                            PhantomProcessHelper.openDeveloperOptions(context)
                        },
                        onOpenBatterySettings = {
                            PhantomProcessHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                } else if (status.isAndroid12Plus) {
                    // Android 12/13: 提供缓解方案
                    Android12Solution(
                        onOpenBatterySettings = {
                            PhantomProcessHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                } else {
                    // Android 12 以下
                    LowAndroidVersionContent()
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 帮助文档
                Text(
                    text = "详细说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightTextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = PhantomProcessHelper.getHelpText(),
                    fontSize = 13.sp,
                    color = lightTextSecondary,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = lightBrandNormal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("知道了", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: PhantomProcessHelper.PhantomProcessStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                status.canUseDeveloperOption -> lightSuccess.copy(alpha = 0.1f)
                status.isAndroid12Plus -> lightBrandNormal.copy(alpha = 0.1f)
                else -> lightSurface06
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        status.canUseDeveloperOption -> Icons.Default.CheckCircle
                        status.isAndroid12Plus -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        status.canUseDeveloperOption -> lightSuccess
                        status.isAndroid12Plus -> lightBrandNormal
                        else -> lightSuccess
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        status.canUseDeveloperOption -> "Android 14+：可完全解决"
                        status.isAndroid12Plus -> "Android 12/13：可部分缓解"
                        else -> "Android 12以下：不受影响"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightTextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when {
                    status.canUseDeveloperOption -> 
                        "您的设备支持在开发者选项中完全禁用后台进程限制"
                    status.isAndroid12Plus -> 
                        "您的设备受系统限制，但可以通过电池优化设置减少被杀概率"
                    else -> 
                        "您的设备版本较低，不受此限制影响"
                },
                fontSize = 13.sp,
                color = lightTextSecondary
            )
        }
    }
}

@Composable
private fun Android14Solution(
    onOpenDeveloperOptions: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Column {
        Text(
            text = "推荐解决方案",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = lightTextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 主要方案：开发者选项
        SolutionButton(
            title = "开启开发者选项设置",
            description = "进入开发者选项，找到\"Disable child process restrictions\"并开启",
            icon = Icons.Default.Build,
            onClick = onOpenDeveloperOptions
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 辅助方案：电池优化
        SolutionButton(
            title = "忽略电池优化",
            description = "将 KimiClaw 设为不优化，减少后台被杀概率",
            icon = Icons.Default.BatterySaver,
            onClick = onOpenBatterySettings
        )
    }
}

@Composable
private fun Android12Solution(
    onOpenBatterySettings: () -> Unit
) {
    Column {
        Text(
            text = "缓解方案",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = lightTextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Android 12/13 无法完全禁用进程限制，但可以：",
            fontSize = 13.sp,
            color = lightTextSecondary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SolutionButton(
            title = "忽略电池优化",
            description = "减少系统后台清理的概率",
            icon = Icons.Default.BatterySaver,
            onClick = onOpenBatterySettings
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 提示卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = lightSurface06)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "使用建议：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = lightTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 保持 KimiClaw 在前台运行\n• 不要划掉 APP 后台\n• 保持通知栏显示",
                    fontSize = 12.sp,
                    color = lightTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LowAndroidVersionContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = lightSuccess.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = lightSuccess,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "无需操作",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightSuccess
                )
                Text(
                    text = "您的设备不受 Phantom Process Killing 影响",
                    fontSize = 13.sp,
                    color = lightTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SolutionButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = lightSurface06)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lightBrandNormal,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightTextPrimary
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = lightTextSecondary,
                    lineHeight = 16.sp
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = lightTextCaption,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


