package com.sgffsg.douyin.assist.common.utils

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 日志工具类
 * 统一管理应用日志输出
 */
object LogUtil {

    private const val DEFAULT_TAG = "DouyinAssistant"
    
    // 是否启用日志输出，可在正式版关闭
    @Volatile
    private var isLogEnabled = true
    @Volatile
    private var fileLogWriter: FileLogWriter? = null

    @Synchronized
    fun init(context: Context, fileLoggingEnabled: Boolean) {
        if (fileLogWriter == null) {
            val appContext = context.applicationContext
            fileLogWriter = FileLogWriter {
                appContext.getExternalFilesDir("logs") ?: File(appContext.filesDir, "logs")
            }
        }
        setFileLoggingEnabled(fileLoggingEnabled)
    }

    fun setFileLoggingEnabled(enabled: Boolean) {
        fileLogWriter?.setEnabled(enabled)
    }

    fun isFileLoggingEnabled(): Boolean = isLogEnabled && fileLogWriter?.isEnabled == true

    /**
     * Verbose 日志
     */
    @JvmStatic
    fun v(tag: String = DEFAULT_TAG, msg: String) = log('V', tag, msg) { Log.v(tag, msg) }

    /**
     * Debug 日志
     */
    @JvmStatic
    fun d(tag: String = DEFAULT_TAG, msg: String) = log('D', tag, msg) { Log.d(tag, msg) }

    /**
     * Info 日志
     */
    @JvmStatic
    fun i(tag: String = DEFAULT_TAG, msg: String) = log('I', tag, msg) { Log.i(tag, msg) }

    /**
     * Warning 日志
     */
    @JvmStatic
    fun w(tag: String = DEFAULT_TAG, msg: String) = log('W', tag, msg) { Log.w(tag, msg) }

    /**
     * Warning 日志（带异常）
     */
    @JvmStatic
    fun w(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) = log('W', tag, msg, tr) { Log.w(tag, msg, tr) }

    /**
     * Error 日志
     */
    @JvmStatic
    fun e(tag: String = DEFAULT_TAG, msg: String) = log('E', tag, msg) { Log.e(tag, msg) }

    /**
     * Error 日志（带异常）
     */
    @JvmStatic
    fun e(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) = log('E', tag, msg, tr) { Log.e(tag, msg, tr) }

    /**
     * What a Terrible Failure 日志
     */
    @JvmStatic
    fun wtf(tag: String = DEFAULT_TAG, msg: String) = log('A', tag, msg) { Log.wtf(tag, msg) }

    /**
     * What a Terrible Failure 日志（带异常）
     */
    @JvmStatic
    fun wtf(tag: String = DEFAULT_TAG, msg: String, tr: Throwable) = log('A', tag, msg, tr) { Log.wtf(tag, msg, tr) }

    private inline fun log(level: Char, tag: String, msg: String, error: Throwable? = null, toLogcat: () -> Unit) {
        if (!isLogEnabled) return
        fileLogWriter?.takeIf { it.isEnabled }?.append(level, tag, msg, error)
        toLogcat()
    }

    /**
     * 设置日志开关
     */
    @JvmStatic
    fun setLogEnabled(enabled: Boolean) {
        isLogEnabled = enabled
    }
}
