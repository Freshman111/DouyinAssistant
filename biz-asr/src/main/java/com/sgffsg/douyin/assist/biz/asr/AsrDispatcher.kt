package com.sgffsg.douyin.assist.biz.asr

import com.sgffsg.douyin.assist.biz.asr.listener.AsrListener

/**
 * @author: lmk
 * @date: 2026/1/2
 * @desc:
 */
class AsrDispatcher private constructor() : AsrListener {
    private val asrListeners = mutableListOf<AsrListener>()

    companion object {
        @Volatile
        private var instance: AsrDispatcher? = null

        fun getInstance(): AsrDispatcher = instance ?: synchronized(this) {
            instance ?: AsrDispatcher().also { instance = it }
        }
    }

    fun addAsrListener(asrListener: AsrListener) {
        if (!asrListeners.contains(asrListener)) {
            asrListeners.add(asrListener)
        }
    }

    fun remoAsrListener(asrListener: AsrListener) {
        asrListeners.remove(asrListener)
    }

    override fun onAsrRecognized(originalText: String) {
        for (listener in asrListeners) {
            listener.onAsrRecognized(originalText)
        }
    }

    override fun onError(error: String) {
        for (listener in asrListeners) {
            listener.onError(error)
        }
    }

    override fun onStateChanged(isListening: Boolean) {
        for (listener in asrListeners) {
            listener.onStateChanged(isListening)
        }
    }


}