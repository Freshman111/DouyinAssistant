package com.sgffsg.douyin.assist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sgffsg.douyin.assist.core.accessibility.utils.AccessibilityHelper
import com.sgffsg.douyin.assist.core.asr.VoiceCommand
import com.sgffsg.douyin.assist.core.asr.VoiceCommandListener
import com.sgffsg.douyin.assist.core.asr.VoiceRecognitionService

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
                Log.e("DouyinAccessibility", "Error finding setting: " + e.message)
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
        initVoiceRecognition()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "窗口状态改变: ${event.packageName}")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容改变时可以检测界面元素
                analyzeCurrentScreen()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAll()
        releaseVoiceRecognition()
        Log.d(TAG, "服务已销毁")
    }

    /**
     * 分析当前屏幕内容
     */
    private fun analyzeCurrentScreen() {
        val rootNode = rootInActiveWindow ?: return
        
        // 检查是否可以滑动
        val canScroll = AccessibilityHelper.isScrollable(rootNode)
        Log.d(TAG, "当前界面可滑动: $canScroll")
        
        // 查找点赞按钮
        val likeButton = findLikeButton(rootNode)
        if (likeButton != null) {
            Log.d(TAG, "找到点赞按钮")
        }
        
        rootNode.recycle()
    }

    /**
     * 查找点赞按钮
     * 抖音的点赞按钮通常有特定的资源ID或描述
     */
    private fun findLikeButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 方法1: 通过资源ID查找（需要根据实际抖音版本调整）
        val likeNodes = rootNode.findAccessibilityNodeInfosByViewId("com.ss.android.ugc.aweme:id/a0x")
        if (likeNodes.isNotEmpty()) {
            return likeNodes[0]
        }
        
        // 方法2: 通过内容描述查找
        val nodesByDesc = AccessibilityHelper.findNodesByText(rootNode, "点赞", false)
        if (nodesByDesc.isNotEmpty()) {
            return nodesByDesc[0]
        }
        
        // 方法3: 通过坐标位置（点赞按钮通常在右下角）
        return findNodeAtRightBottom(rootNode)
    }

    /**
     * 查找右下角的节点（通常是点赞按钮位置）
     */
    private fun findNodeAtRightBottom(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        // 右下角区域
        val targetX = (screenWidth * 0.85).toInt()
        val targetY = (screenHeight * 0.65).toInt()
        
        return AccessibilityHelper.findNodeByPosition(rootNode, targetX, targetY)
    }

    /**
     * 执行点赞操作
     */
    fun performLike() {
        val rootNode = rootInActiveWindow ?: return
        
        val likeButton = findLikeButton(rootNode)
        if (likeButton != null && likeButton.isClickable) {
            likeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "执行点赞操作")
        } else {
            // 如果找不到按钮，使用坐标点击
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            performClick((screenWidth * 0.85).toInt(), (screenHeight * 0.65).toInt())
        }
        
        rootNode.recycle()
    }

    /**
     * 执行向上滑动（下一个视频）
     */
    fun performSwipeUp() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endY = screenHeight * 0.2f
        
        performSwipe(startX, startY, startX, endY, 300)
        Log.d(TAG, "执行向上滑动")
    }

    /**
     * 执行向下滑动（上一个视频）
     */
    fun performSwipeDown() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.2f
        val endY = screenHeight * 0.8f
        
        performSwipe(startX, startY, startX, endY, 300)
        Log.d(TAG, "执行向下滑动")
    }

    /**
     * 执行滑动手势
     */
    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        
        dispatchGesture(builder.build(), null, null)
    }

    /**
     * 执行点击手势
     */
    private fun performClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        dispatchGesture(builder.build(), null, null)
        Log.d(TAG, "执行点击操作: ($x, $y)")
    }

    /**
     * 开始自动点赞
     */
    fun startAutoLike() {
        isAutoLikeEnabled = true
        handler.postDelayed(autoLikeRunnable, likeDelay)
        Log.d(TAG, "开始自动点赞")
    }

    /**
     * 停止自动点赞
     */
    fun stopAutoLike() {
        isAutoLikeEnabled = false
        handler.removeCallbacks(autoLikeRunnable)
        Log.d(TAG, "停止自动点赞")
    }

    /**
     * 开始自动滑动
     */
    fun startAutoSwipe() {
        isAutoSwipeEnabled = true
        handler.postDelayed(autoSwipeRunnable, swipeInterval)
        Log.d(TAG, "开始自动滑动")
    }

    /**
     * 停止自动滑动
     */
    fun stopAutoSwipe() {
        isAutoSwipeEnabled = false
        handler.removeCallbacks(autoSwipeRunnable)
        Log.d(TAG, "停止自动滑动")
    }

    /**
     * 开始自动模式（点赞+滑动）
     */
    fun startAutoMode() {
        isAutoModeEnabled = true
        handler.postDelayed(autoLikeRunnable, likeDelay)
        handler.postDelayed(autoSwipeRunnable, swipeInterval)
        Log.d(TAG, "开始自动模式")
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
        Log.d(TAG, "停止所有操作")
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
                Log.d(TAG, "收到语音指令: ${command.name}, 原始文本: $originalText")
                executeVoiceCommand(command)
            }

            override fun onError(error: String) {
                Log.e(TAG, "语音识别错误: $error")
            }

            override fun onStateChanged(isListening: Boolean) {
                Log.d(TAG, "语音识别状态: ${if (isListening) "监听中" else "已停止"}")
            }
        })

        // 初始化模型
        voiceRecognitionService?.initialize { success, error ->
            if (success) {
                Log.d(TAG, "语音识别模型初始化成功")
            } else {
                Log.e(TAG, "语音识别模型初始化失败: $error")
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
            Log.w(TAG, "语音控制已开启")
            return true
        }

        val success = voiceRecognitionService?.startListening() ?: false
        if (success) {
            isVoiceControlEnabled = true
            Log.d(TAG, "语音控制已开启")
        } else {
            Log.e(TAG, "语音控制开启失败")
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
        Log.d(TAG, "语音控制已关闭")
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
                performSwipeUp()
                Log.d(TAG, "执行语音指令: 上滑")
            }
            VoiceCommand.SWIPE_DOWN -> {
                performSwipeDown()
                Log.d(TAG, "执行语音指令: 下滑")
            }
            VoiceCommand.NEXT_PAGE -> {
                performSwipeUp()
                Log.d(TAG, "执行语音指令: 下一页")
            }
            VoiceCommand.PREVIOUS_PAGE -> {
                performSwipeDown()
                Log.d(TAG, "执行语音指令: 上一页")
            }
            VoiceCommand.SKIP -> {
                performSwipeUp()
                Log.d(TAG, "执行语音指令: 跳过")
            }
            VoiceCommand.LIKE -> {
                performLike()
                Log.d(TAG, "执行语音指令: 点赞")
            }
            VoiceCommand.BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d(TAG, "执行语音指令: 返回")
            }
        }
    }
}
