package com.sgffsg.douyin.assist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.sgffsg.douyin.assist.core.asr.VoiceCommand
import com.sgffsg.douyin.assist.core.asr.VoiceCommandListener
import com.sgffsg.douyin.assist.core.asr.VoiceRecognitionService
import com.sgffsg.douyin.assist.util.LogUtil

class AssistAccessibilityService : AccessibilityService() {

    private val TAG = "AssistAccessibility"
    private val handler = Handler(Looper.getMainLooper())
    
    private var isAutoLikeEnabled = false
    private var isAutoSwipeEnabled = false
    private var isAutoModeEnabled = false
    
    private var swipeInterval = 5000L // 滑动间隔，默认5秒
    private var likeDelay = 1000L // 点赞延迟，默认1秒
    
    // 语音识别服务
    private var voiceRecognitionService: VoiceRecognitionService? = null
    private var isVoiceControlEnabled = false
    
    // 手势执行器
    private lateinit var performer: AssistAccessibilityPerform

    private val autoSwipeRunnable = object : Runnable {
        override fun run() {
            if (isAutoSwipeEnabled || isAutoModeEnabled) {
                performSwipeUp()
                handler.postDelayed(this, swipeInterval)
            }
        }
    }

    private val autoLikeRunnable = object : Runnable {
        override fun run() {
            if (isAutoLikeEnabled || isAutoModeEnabled) {
                performLike()
                handler.postDelayed(this, swipeInterval)
            }
        }
    }

    companion object {
        var instance: AssistAccessibilityService? = null

        fun isAccessibilitySettingsOn(context: Context): Boolean {
            var accessibilityEnabled = 0
            val service = context.packageName + "/" + AssistAccessibilityService::class.java.canonicalName
            try {
                accessibilityEnabled = Settings.Secure.getInt(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                LogUtil.e("DouyinAccessibility", "Error finding setting: " + e.message)
            }

            val colonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (settingValue != null) {
                    colonSplitter.setString(settingValue)
                    while (colonSplitter.hasNext()) {
                        val accessibilityService = colonSplitter.next()
                        if (accessibilityService.equals(service, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        performer = AssistAccessibilityPerform(this)
        initVoiceRecognition()
        LogUtil.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                LogUtil.d(TAG, "窗口状态改变: ${event.packageName}")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容改变时可以检测界面元素
                performer.analyzeCurrentScreen()
            }
        }
    }

    override fun onInterrupt() {
        LogUtil.d(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAll()
        releaseVoiceRecognition()
        LogUtil.d(TAG, "服务已销毁")
    }

    /**
     * 执行点赞操作
     */
    fun performLike() {
        performer.performLike()
    }

    /**
     * 执行向上滑动（下一个视频）
     */
    fun performSwipeUp() {
        performer.performSwipeUp()
    }

    /**
     * 执行向下滑动（上一个视频）
     */
    fun performSwipeDown() {
        performer.performSwipeDown()
    }

    /**
     * 开始自动点赞
     */
    fun startAutoLike() {
        isAutoLikeEnabled = true
        handler.postDelayed(autoLikeRunnable, likeDelay)
        LogUtil.d(TAG, "开始自动点赞")
    }

    /**
     * 停止自动点赞
     */
    fun stopAutoLike() {
        isAutoLikeEnabled = false
        handler.removeCallbacks(autoLikeRunnable)
        LogUtil.d(TAG, "停止自动点赞")
    }

    /**
     * 开始自动滑动
     */
    fun startAutoSwipe() {
        isAutoSwipeEnabled = true
        handler.postDelayed(autoSwipeRunnable, swipeInterval)
        LogUtil.d(TAG, "开始自动滑动")
    }

    /**
     * 停止自动滑动
     */
    fun stopAutoSwipe() {
        isAutoSwipeEnabled = false
        handler.removeCallbacks(autoSwipeRunnable)
        LogUtil.d(TAG, "停止自动滑动")
    }

    /**
     * 开始自动模式（点赞+滑动）
     */
    fun startAutoMode() {
        isAutoModeEnabled = true
        handler.postDelayed(autoLikeRunnable, likeDelay)
        handler.postDelayed(autoSwipeRunnable, swipeInterval)
        LogUtil.d(TAG, "开始自动模式")
    }

    /**
     * 停止所有自动操作
     */
    fun stopAll() {
        isAutoLikeEnabled = false
        isAutoSwipeEnabled = false
        isAutoModeEnabled = false
        handler.removeCallbacks(autoLikeRunnable)
        handler.removeCallbacks(autoSwipeRunnable)
        LogUtil.d(TAG, "停止所有操作")
    }

    /**
     * 设置滑动间隔
     */
    fun setSwipeInterval(interval: Long) {
        swipeInterval = interval
    }

    /**
     * 设置点赞延迟
     */
    fun setLikeDelay(delay: Long) {
        likeDelay = delay
    }

    // ==================== 语音识别相关方法 ====================

    /**
     * 初始化语音识别服务
     */
    private fun initVoiceRecognition() {
        voiceRecognitionService = VoiceRecognitionService(this)
        voiceRecognitionService?.setVoiceCommandListener(object : VoiceCommandListener {
            override fun onCommandRecognized(command: VoiceCommand, originalText: String) {
                LogUtil.d(TAG, "收到语音指令: ${command.name}, 原始文本: $originalText")
                executeVoiceCommand(command)
            }

            override fun onError(error: String) {
                LogUtil.e(TAG, "语音识别错误: $error")
            }

            override fun onStateChanged(isListening: Boolean) {
                LogUtil.d(TAG, "语音识别状态: ${if (isListening) "监听中" else "已停止"}")
            }
        })

        // 初始化模型
        voiceRecognitionService?.initialize { success, error ->
            if (success) {
                LogUtil.d(TAG, "初始化成功")
            } else {
                LogUtil.e(TAG, "初始化失败: $error")
            }
        }
    }

    /**
     * 释放语音识别资源
     */
    private fun releaseVoiceRecognition() {
        voiceRecognitionService?.release()
        voiceRecognitionService = null
        isVoiceControlEnabled = false
    }

    /**
     * 开启语音控制
     */
    fun startVoiceControl(): Boolean {
        if (isVoiceControlEnabled) {
            LogUtil.w(TAG, "语音控制已开启")
            return true
        }

        val success = voiceRecognitionService?.startListening() ?: false
        if (success) {
            isVoiceControlEnabled = true
            LogUtil.d(TAG, "语音控制已开启")
        } else {
            LogUtil.e(TAG, "语音控制开启失败")
        }
        return success
    }

    /**
     * 关闭语音控制
     */
    fun stopVoiceControl() {
        if (!isVoiceControlEnabled) {
            return
        }

        voiceRecognitionService?.stopListening()
        isVoiceControlEnabled = false
        LogUtil.d(TAG, "语音控制已关闭")
    }

    /**
     * 暂停/恢复语音识别
     */
    fun pauseVoiceControl(pause: Boolean) {
        voiceRecognitionService?.setPause(pause)
    }

    /**
     * 检查语音控制状态
     */
    fun isVoiceControlEnabled(): Boolean = isVoiceControlEnabled

    /**
     * 执行语音指令
     */
    private fun executeVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.SWIPE_UP -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 上滑")
            }
            VoiceCommand.SWIPE_DOWN -> {
                performer.performSwipeDown()
                LogUtil.d(TAG, "执行语音指令: 下滑")
            }
            VoiceCommand.NEXT_PAGE -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 下一页")
            }
            VoiceCommand.PREVIOUS_PAGE -> {
                performer.performSwipeDown()
                LogUtil.d(TAG, "执行语音指令: 上一页")
            }
            VoiceCommand.SKIP -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 跳过")
            }
            VoiceCommand.LIKE -> {
                performer.performLike()
                LogUtil.d(TAG, "执行语音指令: 点赞")
            }
            VoiceCommand.BACK -> {
                performer.performBack()
                LogUtil.d(TAG, "执行语音指令: 返回")
            }
        }
    }
}
