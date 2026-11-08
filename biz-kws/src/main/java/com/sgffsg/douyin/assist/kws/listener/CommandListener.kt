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
     * @param logSwipe 记录滑动派发和完成状态的可选诊断回调
     */
    fun onCommandRecognized(command: Command, originalText: String, logSwipe: ((String) -> Unit)?)
    
    /**
     * 识别错误
     * @param error 错误信息
     */
    fun onError(error: String)
}
