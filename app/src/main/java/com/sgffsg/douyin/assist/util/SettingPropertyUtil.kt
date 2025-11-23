package com.sgffsg.douyin.assist.util

import android.util.Log
import java.lang.Exception
import java.lang.reflect.Method

/**
 * 设置系统属性的工具类
 */
object SettingPropertyUtil {
    val TAG = javaClass.simpleName


    /**
     * 设置是否继续保持开启语音助手系统唤醒功能，开启系统算法后会默认开启降噪算法，
     * wakeUp true 标识设置保持语音唤醒
     */
    fun setSystemStayWakeUp(wakeUp: Boolean) {
        Log.i(TAG, "setSystemStayWakeUp --- wakeUp = $wakeUp")
        val key = "persist.youdao.voice.preprocessing.enable"
        try {
            val c = Class.forName("android.os.SystemProperties")
            val set: Method = c.getMethod("set", String::class.java, String::class.java)
            set.invoke(c, key, if (wakeUp) "1" else "0")
        } catch (e: Exception) {
            Log.i(TAG, "setSystemStayWakeUp e = $e")
        }
    }

    /**
     * wakeUp true 获取保持语音唤醒标识
     */
    fun getSystemStayWakeUp(): String {
        val key = "persist.youdao.voice.preprocessing.enable"
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get: Method = c.getMethod("get", String::class.java, String::class.java)
            get.invoke(c, key, "unknown") as String
        } catch (e: Exception) {
            Log.i(TAG, "getSystemStayWakeUp e = $e")
            "unknown-e"
        }
    }

    /**
     * 设置是否继续保持开启语音助手系统唤醒功能，开启系统算法后会默认开启降噪算法，
     * wakeUp true 标识设置保持语音唤醒
     */
    fun setSystemStayWakeUpPhoenix(wakeUp: Boolean) {
        Log.i(TAG, "setSystemStayWakeUpPhoenix --- wakeUp = $wakeUp")
        val key = "persist.sys.youdao.voice.preprocessing.enable"
        try {
            val c = Class.forName("android.os.SystemProperties")
            val set: Method = c.getMethod("set", String::class.java, String::class.java)
            set.invoke(c, key, if (wakeUp) "1" else "0")
        } catch (e: Exception) {
            Log.i(TAG, "setSystemStayWakeUpPhoenix e = $e")
        }
    }

}