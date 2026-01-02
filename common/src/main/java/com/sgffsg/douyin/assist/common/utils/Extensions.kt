package com.sgffsg.douyin.assist.common.utils

import android.view.View

/**
 * @author: lmk
 * @date: 2024/5/8
 * @desc:
 */
private var <T : View> T.triggerLastTime: Long
    get() = if (getTag(1123460103) != null) getTag(1123460103) as Long else -601
    set(value) {
        setTag(1123460103, value)
    }

private var <T : View> T.triggerDelay: Long
    get() = if (getTag(1123461123) != null) getTag(1123461123) as Long else 600
    set(value) {
        setTag(1123461123, value)
    }

@JvmOverloads
fun <T : View> T.clickWithTrigger(time: Long = 400, block: (T) -> Unit) {
    triggerDelay = time
    setOnClickListener {
        if (clickEnable()) {
            block(it as T)
        }
    }
}

private fun <T : View> T.clickEnable(): Boolean {
    var flag = false
    val currentClickTime = System.currentTimeMillis()
    if (currentClickTime - triggerLastTime >= triggerDelay) {
        flag = true
    }
    LogUtil.d("Extensions", "fastClick: $flag $currentClickTime $triggerLastTime")
    triggerLastTime = currentClickTime
    return flag
}