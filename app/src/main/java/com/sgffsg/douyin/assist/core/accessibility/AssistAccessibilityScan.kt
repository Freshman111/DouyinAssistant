package com.sgffsg.douyin.assist.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.sgffsg.douyin.assist.core.accessibility.utils.AccessibilityHelper

/**
 * 无障碍服务界面扫描分析类
 * 负责分析当前屏幕内容，查找界面元素
 */
class AssistAccessibilityScan(
    private val service: AccessibilityService
) {

    private val TAG = "AccessibilityScan"

    /**
     * 分析当前屏幕内容
     */
    fun analyzeCurrentScreen() {
        val rootNode = service.rootInActiveWindow ?: return
        
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
    fun findLikeButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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
    fun findNodeAtRightBottom(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        
        // 右下角区域
        val targetX = (screenWidth * 0.85).toInt()
        val targetY = (screenHeight * 0.65).toInt()
        
        return AccessibilityHelper.findNodeByPosition(rootNode, targetX, targetY)
    }

    /**
     * 检查当前界面是否可滑动
     */
    fun isCurrentScreenScrollable(): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val canScroll = AccessibilityHelper.isScrollable(rootNode)
        rootNode.recycle()
        return canScroll
    }

    /**
     * 查找指定文本的节点
     */
    fun findNodesByText(text: String, exact: Boolean = true): List<AccessibilityNodeInfo> {
        val rootNode = service.rootInActiveWindow ?: return emptyList()
        val nodes = AccessibilityHelper.findNodesByText(rootNode, text, exact)
        rootNode.recycle()
        return nodes
    }

    /**
     * 查找指定类名的节点
     */
    fun findNodesByClassName(className: String): List<AccessibilityNodeInfo> {
        val rootNode = service.rootInActiveWindow ?: return emptyList()
        val nodes = AccessibilityHelper.findNodesByClassName(rootNode, className)
        rootNode.recycle()
        return nodes
    }

    /**
     * 根据坐标查找节点
     */
    fun findNodeByPosition(x: Int, y: Int): AccessibilityNodeInfo? {
        val rootNode = service.rootInActiveWindow ?: return null
        val node = AccessibilityHelper.findNodeByPosition(rootNode, x, y)
        rootNode.recycle()
        return node
    }

    /**
     * 打印当前界面节点树（调试用）
     */
    fun printCurrentScreenTree() {
        val rootNode = service.rootInActiveWindow ?: return
        Log.d(TAG, "========== 界面节点树 ==========")
        AccessibilityHelper.printNodeTree(rootNode)
        Log.d(TAG, "================================")
        rootNode.recycle()
    }
}
