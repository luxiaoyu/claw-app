package com.moonshot.kimiclaw.ui

import com.termux.R
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import androidx.core.net.toUri

/**
 * @desc   : 视频开屏页，全屏播放视频后自动跳转到主界面
 */

@OptIn(UnstableApi::class)
@Composable
fun VideoSplashScreen(
    onVideoComplete: () -> Unit,
    minDisplayTimeMs: Long = 2000L, // 最少显示时间，避免视频太短一闪而过
    autoAdvanceDelayMs: Long = 500L,  // 视频结束后自动跳转的延迟
    forceLandscape: Boolean = true    // 是否强制横屏播放
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }

    // 强制横屏播放 + 隐藏状态栏
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation

        if (forceLandscape && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // 隐藏状态栏和导航栏（全屏沉浸模式）
        var originalSystemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            originalSystemBarsBehavior = windowInsetsController.systemBarsBehavior
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // 恢复原始方向（InstallScreen 需要竖屏）
            // 由于 AndroidManifest 配置了 configChanges="orientation"，
            // 屏幕方向改变不会导致 Activity 重建，安装流程不会中断
            activity?.requestedOrientation = originalOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // 恢复状态栏和导航栏
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = originalSystemBarsBehavior
            }
        }
    }

    // 创建 ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // 设置视频资源
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_video}")
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)

            // 配置播放参数
            repeatMode = Player.REPEAT_MODE_OFF  // 不循环播放
            playWhenReady = true                 // 准备好后自动播放
            volume = 0f                          // 静音播放

            // 添加监听器
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            isVideoReady = true
                            if (startTime == 0L) {
                                startTime = System.currentTimeMillis()
                            }
                        }

                        Player.STATE_ENDED -> {
                            // 视频播放结束，计算是否需要等待最小显示时间
                            val elapsedTime = System.currentTimeMillis() - startTime
                            val remainingTime = (minDisplayTimeMs - elapsedTime).coerceAtLeast(0)

                            // 延迟跳转
                            // 注意：这里使用 DisposableEffect 清理，实际跳转在 LaunchedEffect 中处理
                        }
                    }
                }
            })

            prepare()
        }
    }

    // 监听视频结束并处理跳转
    LaunchedEffect(exoPlayer) {
        var hasCompleted = false

        while (!hasCompleted) {
            delay(100) // 每100ms检查一次

            val playbackState = exoPlayer.playbackState
            if (playbackState == Player.STATE_ENDED) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = (minDisplayTimeMs - elapsedTime).coerceAtLeast(0)

                delay(remainingTime + autoAdvanceDelayMs)
                hasCompleted = true
                onVideoComplete()
            }
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 全屏视频播放器
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // 使用 ZOOM 模式填满屏幕（可能裁剪部分内容）
                    // 横屏模式下视频会完整展示并填满屏幕
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    // 隐藏所有控制 UI
                    useController = false
                    // 保持屏幕常亮
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 带跳过按钮的视频开屏页
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSplashScreenWithSkip(
    onVideoComplete: () -> Unit,
    onSkip: () -> Unit,
    minDisplayTimeMs: Long = 2000L,
    showSkipDelayMs: Long = 3000L,  // 多少秒后显示跳过按钮
    forceLandscape: Boolean = true   // 是否强制横屏播放
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }
    var showSkipButton by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0L) }

    // 强制横屏播放 + 隐藏状态栏
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation

        if (forceLandscape && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // 隐藏状态栏和导航栏（全屏沉浸模式）
        var originalSystemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            originalSystemBarsBehavior = windowInsetsController.systemBarsBehavior
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // 恢复原始方向
            activity?.requestedOrientation = originalOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // 恢复状态栏和导航栏
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = originalSystemBarsBehavior
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = "android.resource://${context.packageName}/${R.raw.splash_video}".toUri()
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            volume = 0f

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            isVideoReady = true
                            if (startTime == 0L) {
                                startTime = System.currentTimeMillis()
                            }
                        }

                        Player.STATE_ENDED -> {
                            // 视频结束，等待最小显示时间后跳转
                        }
                    }
                }
            })

            prepare()
        }
    }

    // 更新已显示时间和跳过按钮状态
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(100)
            if (startTime > 0) {
                elapsedTime = System.currentTimeMillis() - startTime
                showSkipButton = elapsedTime >= showSkipDelayMs
            }

            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                val remainingTime = (minDisplayTimeMs - elapsedTime).coerceAtLeast(0)
                delay(remainingTime + 500)
                onVideoComplete()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // 使用 ZOOM 模式填满屏幕（可能裁剪部分内容）
                    // 横屏模式下视频会完整展示并填满屏幕
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 跳过按钮（可选）
        // 如果需要显示跳过按钮，可以在这里添加
    }
}
