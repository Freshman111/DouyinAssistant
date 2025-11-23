package com.sgffsg.douyin.assist.app

import android.app.Application
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * @author: lmk
 * @date: 2025/11/23
 * @desc:
 */
class AssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogUtil.i("AssistApp","onCrete")
    }
}