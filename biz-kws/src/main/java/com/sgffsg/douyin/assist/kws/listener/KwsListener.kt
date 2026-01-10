package com.sgffsg.douyin.assist.kws.listener

/**
 * 语音指令监听器
 */
interface KwsListener {
    /**
     * 识别到语音指令
     * @param originalText 原始识别文本
     */
    fun onAsrRecognized(originalText: String)
    
    /**
     * 识别错误
     * @param error 错误信息
     */
    fun onError(error: String)
    
    /**
     * ASR状态变化
     * @param isListening 是否正在监听
     */
    fun onStateChanged(isListening: Boolean)
}
