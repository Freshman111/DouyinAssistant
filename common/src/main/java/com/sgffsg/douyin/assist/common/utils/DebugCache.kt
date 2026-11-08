package com.sgffsg.douyin.assist.common.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Debug功能缓存类
 * 用于存储Debug界面的各种开关状态
 */
object DebugCache {

    private const val TAG = "DebugCache"

    private const val DEBUG_PREF_NAME = "debug_preferences"
    private const val KEY_SAVE_AUDIO_ENABLED = "save_audio_enabled"
    private const val KEY_FILE_LOGGING_ENABLED = "file_logging_enabled"

    private var sharedPreferences: SharedPreferences? = null

    /**
     * 初始化缓存
     */
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(DEBUG_PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 设置音频保存功能开关状态
     */
    fun setSaveAudioEnabled(enabled: Boolean) {
        LogUtil.i(TAG, "setSaveAudioEnabled enabled=$enabled")
        sharedPreferences?.edit()?.putBoolean(KEY_SAVE_AUDIO_ENABLED, enabled)?.apply()
    }

    /**
     * 获取音频保存功能开关状态
     */
    fun isSaveAudioEnabled(): Boolean {
        return sharedPreferences?.getBoolean(KEY_SAVE_AUDIO_ENABLED, false) ?: false
    }

    fun setFileLoggingEnabled(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean(KEY_FILE_LOGGING_ENABLED, enabled)?.apply()
        LogUtil.setFileLoggingEnabled(enabled)
    }

    fun isFileLoggingEnabled(): Boolean =
        sharedPreferences?.getBoolean(KEY_FILE_LOGGING_ENABLED, false) ?: false

    /**
     * 获取SharedPreferences实例
     */
    fun getSharedPreferences(): SharedPreferences? {
        return sharedPreferences
    }
}
