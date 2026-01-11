package com.sgffsg.douyin.assist.app

import android.app.Application
import com.didi.drouter.api.DRouter
import com.sgffsg.douyin.assist.common.utils.DebugCache
import com.sgffsg.douyin.assist.common.utils.LogUtil

/**
 * @author: lmk
 * @date: 2025/11/23
 * @desc:
 */
class AssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogUtil.i("AssistApp","onCrete")
        DRouter.init(this)
        
        // 初始化DebugCache
        DebugCache.init(this)
    }
}