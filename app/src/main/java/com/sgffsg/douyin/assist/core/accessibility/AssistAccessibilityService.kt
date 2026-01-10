package com.sgffsg.douyin.assist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.sgffsg.douyin.assist.common.utils.LogUtil
import com.sgffsg.douyin.assist.kws.Command
import com.sgffsg.douyin.assist.kws.KwsRecognitionService
import com.sgffsg.douyin.assist.kws.listener.CommandListener

class AssistAccessibilityService : AccessibilityService() {

    // 扫描线程
    private lateinit var scanThread: HandlerThread
    private lateinit var scanHandler: Handler
    
    // 语音识别服务
    private var voiceRecognitionService: KwsRecognitionService? = null
    private var isVoiceControlEnabled = false
    
    // 手势执行器
    private lateinit var performer: AssistAccessibilityPerform
    
    // 界面扫描器
    private lateinit var scanner: AssistAccessibilityScan

    companion object {
        const val TAG = "AssistAccessibility"
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
                LogUtil.e(TAG, "Error finding setting: " + e.message)
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
        
        // 初始化扫描线程
        scanThread = HandlerThread("AccessibilityScanThread").apply {
            start()
        }
        scanHandler = Handler(scanThread.looper)
        
        scanner = AssistAccessibilityScan(this)
        performer = AssistAccessibilityPerform(this, scanner)
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
                // 内容改变时在子线程中检测界面元素
//                scanHandler.post {
//                    scanner.analyzeCurrentScreen()
//                }
            }
        }
    }

    override fun onInterrupt() {
        LogUtil.d(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        releaseVoiceRecognition()
        
        // 释放扫描线程
        scanHandler.removeCallbacksAndMessages(null)
        scanThread.quitSafely()
        
        LogUtil.d(TAG, "服务已销毁")
    }

    // ==================== 语音识别相关方法 ====================

    /**
     * 初始化语音识别服务
     */
    private fun initVoiceRecognition() {
        voiceRecognitionService = KwsRecognitionService(this)
        voiceRecognitionService?.setVoiceCommandListener(object : CommandListener {
            override fun onCommandRecognized(command: Command, originalText: String) {
                LogUtil.d(TAG, "收到语音指令: ${command.name}, 原始文本: $originalText")
                executeVoiceCommand(command)
            }

            override fun onError(error: String) {
                LogUtil.e(TAG, "语音识别错误: $error")
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
     * 执行语音指令
     */
    private fun executeVoiceCommand(command: Command) {
        when (command) {
            Command.SWIPE_UP -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 上滑")
            }
            Command.SWIPE_DOWN -> {
                performer.performSwipeDown()
                LogUtil.d(TAG, "执行语音指令: 下滑")
            }
            Command.NEXT_PAGE -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 下一页")
            }
            Command.PREVIOUS_PAGE -> {
                performer.performSwipeDown()
                LogUtil.d(TAG, "执行语音指令: 上一页")
            }
            Command.SKIP -> {
                performer.performSwipeUp()
                LogUtil.d(TAG, "执行语音指令: 跳过")
            }
            Command.LIKE -> {
                performer.performLike()
                LogUtil.d(TAG, "执行语音指令: 点赞")
            }
            Command.BACK -> {
                performer.performBack()
                LogUtil.d(TAG, "执行语音指令: 返回")
            }
        }
    }
}
