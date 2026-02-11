package com.moonshot.kimiclaw

import com.termux.app.TermuxActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.moonshot.kimiclaw.theme.lightBrandNormal
import com.moonshot.kimiclaw.theme.lightMainSurface
import com.moonshot.kimiclaw.theme.lightTextPrimary
import com.moonshot.kimiclaw.theme.lightTextSecondary
import com.moonshot.kimiclaw.ui.WelcomeScreen
import com.moonshot.kimiclaw.viewmodel.WelcomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WelcomeViewModel by viewModels()

    // 用于启动通知设置页面并接收返回结果
    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从通知设置页面返回，检查权限状态
        viewModel.onReturnFromNotificationSettings()
    }

    // 用于启动电池优化设置页面并接收返回结果
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从电池优化设置页面返回，检查权限状态
        viewModel.onReturnFromBatteryOptimizationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set status bar icons to dark (for light background)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // 收集 ViewModel 事件
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.openNotificationSettingsEvent.collect { intent ->
                        notificationSettingsLauncher.launch(intent)
                    }
                }
                launch {
                    viewModel.openBatteryOptimizationEvent.collect { intent ->
                        batteryOptimizationLauncher.launch(intent)
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = lightMainSurface
                ) {
                    var showWelcomeScreen by remember { mutableStateOf(false) }

                    if (showWelcomeScreen) {
                        WelcomeScreen(
                            viewModel = viewModel,
                            onNext = {
                                // Start TermuxActivity
                                val intent = Intent(this, TermuxActivity::class.java)
                                startActivity(intent)
                                finish()
                            },
                            onCheckUpgrade = {
                                // Handle check upgrade
                            }
                        )
                    } else {
                        MainContent(
                            onShowWelcomeScreen = {
                                showWelcomeScreen = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回页面时检查权限状态
        viewModel.checkNotificationPermission()
        viewModel.checkBatteryOptimizationStatus()
    }
}

@Composable
fun MainContent(onShowWelcomeScreen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightMainSurface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AndroidClaw",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = lightTextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "OpenClaw Gateway for Android",
            fontSize = 16.sp,
            color = lightTextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // WelcomeScreen button
        Button(
            onClick = onShowWelcomeScreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = lightBrandNormal
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "WelcomeScreen",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
