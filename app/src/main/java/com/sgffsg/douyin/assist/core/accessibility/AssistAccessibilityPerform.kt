package com.sgffsg.douyin.assist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * 无障碍服务手势执行类
 * 负责执行各种手势操作
 */
class AssistAccessibilityPerform(
    private val service: AccessibilityService,
    private val scanner: AssistAccessibilityScan
) {

    private val TAG = "AccessibilityPerform"

    /**
     * 执行点赞操作
     */
    fun performLike() {
        val rootNode = service.rootInActiveWindow ?: return
        
        val likeButton = scanner.findLikeButton(rootNode)
        if (likeButton != null && likeButton.isClickable) {
            likeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            LogUtil.d(TAG, "执行点赞操作")
        } else {
            // 如果找不到按钮，使用坐标点击
            val screenWidth = service.resources.displayMetrics.widthPixels
            val screenHeight = service.resources.displayMetrics.heightPixels
            performClick((screenWidth * 0.85).toInt(), (screenHeight * 0.65).toInt())
        }
        
        rootNode.recycle()
    }

    /**
     * 执行向上滑动（下一个视频）
     */
    fun performSwipeUp() {
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endY = screenHeight * 0.2f
        
        performSwipe(startX, startY, startX, endY, 300)
        LogUtil.d(TAG, "执行向上滑动")
    }

    /**
     * 执行向下滑动（上一个视频）
     */
    fun performSwipeDown() {
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.2f
        val endY = screenHeight * 0.8f
        
        performSwipe(startX, startY, startX, endY, 300)
        LogUtil.d(TAG, "执行向下滑动")
    }

    /**
     * 执行返回操作
     */
    fun performBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        LogUtil.d(TAG, "执行返回操作")
    }

    /**
     * 执行Home操作
     */
    fun performHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        LogUtil.d(TAG, "执行Home操作")
    }

    /**
     * 执行最近任务操作
     */
    fun performRecents() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        LogUtil.d(TAG, "执行最近任务操作")
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
        
        service.dispatchGesture(builder.build(), null, null)
    }

    /**
     * 执行点击手势
     */
    private fun performClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        service.dispatchGesture(builder.build(), null, null)
        LogUtil.d(TAG, "执行点击操作: ($x, $y)")
    }
}
