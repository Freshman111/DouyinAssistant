package com.sgffsg.douyin.assist.recorder

import android.media.AudioFormat

/**
 * Created by xuyang on 2021/1/20.
 */
class AudioType private constructor(
    //采样率
    val sampleRateInHz: Int,
    //音频格式
    val audioFormat: Int,
    val sampleSizeInBytes: Int
) {

    fun bitsPerSample(): Int {
        return sampleSizeInBytes * 8
    }

    companion object {
        val PCM_16K = AudioType(16000, AudioFormat.ENCODING_PCM_16BIT, 2)
    }
}