package com.sgffsg.douyin.assist.audio.recorder

/**
 * @author: lmk
 * @date: 2024/5/8
 * @desc: 录音器接口
 */
interface IRecorder {

    /**
     * 开始录音
     */
    fun startRecord()

    /**
     * 暂停后再次录音
     */
    fun resumeRecord()

    /**
     * 暂停录音
     */
    fun pauseRecord()

    /**
     * 停止录音
     */
    fun stopRecord()

    /**
     * 是否在录音
     */
    fun isRecording()

    /**
     * 是否暂停
     */
    fun isPausing()
}