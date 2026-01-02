package com.sgffsg.douyin.assist.common.utils

import android.util.Log

/**
 * 日志工具类
 * 统一管理应用日志输出
 */
object LogUtil {

    private const val DEFAULT_TAG = "DouyinAssistant"
    
    // 是否启用日志输出，可在正式版关闭
    private var isLogEnabled = true

    /**
     * Verbose 日志
     */
    @JvmStatic
    fun v(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.v(tag, msg)
        }
    }

    /**
     * Debug 日志
     */
    @JvmStatic
    fun d(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.d(tag, msg)
        }
    }

    /**
     * Info 日志
     */
    @JvmStatic
    fun i(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.i(tag, msg)
        }
    }

    /**
     * Warning 日志
     */
    @JvmStatic
    fun w(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.w(tag, msg)
        }
    }

    /**
     * Warning 日志（带异常）
     */
    @JvmStatic
    fun w(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) {
        if (isLogEnabled) {
            Log.w(tag, msg, tr)
        }
    }

    /**
     * Error 日志
     */
    @JvmStatic
    fun e(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.e(tag, msg)
        }
    }

    /**
     * Error 日志（带异常）
     */
    @JvmStatic
    fun e(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) {
        if (isLogEnabled) {
            Log.e(tag, msg, tr)
        }
    }

    /**
     * What a Terrible Failure 日志
     */
    @JvmStatic
    fun wtf(tag: String = DEFAULT_TAG, msg: String) {
        if (isLogEnabled) {
            Log.wtf(tag, msg)
        }
    }

    /**
     * What a Terrible Failure 日志（带异常）
     */
    @JvmStatic
    fun wtf(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) {
        if (isLogEnabled) {
            Log.wtf(tag, msg, tr)
        }
    }

    /**
     * 设置日志开关
     */
    @JvmStatic
    fun setLogEnabled(enabled: Boolean) {
        isLogEnabled = enabled
    }
}
