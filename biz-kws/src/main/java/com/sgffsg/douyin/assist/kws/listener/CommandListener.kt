package com.sgffsg.douyin.assist.kws.listener

import com.sgffsg.douyin.assist.kws.Command

/**
 * 语音指令监听器
 */
interface CommandListener {
    /**
     * 识别到语音指令
     * @param command 语音指令
     * @param originalText 原始识别文本
     */
    fun onCommandRecognized(command: Command, originalText: String)
    
    /**
     * 识别错误
     * @param error 错误信息
     */
    fun onError(error: String)
}
