package com.sgffsg.douyin.assist.core.accessibility.utils

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityHelper {

    /**
     * 检查节点是否可滑动
     */
    fun isScrollable(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        return node.isScrollable || hasScrollableChild(node)
    }

    /**
     * 递归检查是否有可滑动的子节点
     */
    private fun hasScrollableChild(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isScrollable) {
                child.recycle()
                return true
            }
            if (hasScrollableChild(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    /**
     * 通过文本查找节点
     */
    fun findNodesByText(rootNode: AccessibilityNodeInfo, text: String, exact: Boolean = true): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesByTextRecursive(rootNode, text, exact, result)
        return result
    }

    private fun findNodesByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        exact: Boolean,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        val matches = if (exact) {
            nodeText == text || contentDesc == text
        } else {
            nodeText.contains(text) || contentDesc.contains(text)
        }
        
        if (matches) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByTextRecursive(child, text, exact, result)
        }
    }

    /**
     * 通过类名查找节点
     */
    fun findNodesByClassName(rootNode: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassNameRecursive(rootNode, className, result)
        return result
    }

    private fun findNodesByClassNameRecursive(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassNameRecursive(child, className, result)
        }
    }

    /**
     * 根据坐标位置查找节点
     */
    fun findNodeByPosition(rootNode: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        rootNode.getBoundsInScreen(rect)
        
        if (rect.contains(x, y)) {
            // 先检查子节点
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChild(i) ?: continue
                val result = findNodeByPosition(child, x, y)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
            // 如果子节点都不包含该点，返回当前节点
            return rootNode
        }
        return null
    }

    /**
     * 检查节点是否可点击
     */
    fun isClickable(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        return node.isClickable || hasClickableParent(node)
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                parent.recycle()
                return true
            }
            val temp = parent.parent
            parent.recycle()
            parent = temp
        }
        return false
    }

    /**
     * 打印节点树结构（调试用）
     */
    fun printNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        node ?: return
        
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        
        println("$indent[$className] text='$text' desc='$desc' id='$viewId' " +
                "clickable=${node.isClickable} scrollable=${node.isScrollable}")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            printNodeTree(child, depth + 1)
            child.recycle()
        }
    }

    /**
     * 查找可点击的节点
     */
    fun findClickableNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findClickableNodesRecursive(rootNode, result)
        return result
    }

    private fun findClickableNodesRecursive(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isClickable) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableNodesRecursive(child, result)
        }
    }

    /**
     * 获取节点的边界矩形
     */
    fun getNodeBounds(node: AccessibilityNodeInfo): Rect {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect
    }

    /**
     * 检查两个节点是否重叠
     */
    fun isNodesOverlapping(node1: AccessibilityNodeInfo, node2: AccessibilityNodeInfo): Boolean {
        val rect1 = getNodeBounds(node1)
        val rect2 = getNodeBounds(node2)
        return Rect.intersects(rect1, rect2)
    }
}
