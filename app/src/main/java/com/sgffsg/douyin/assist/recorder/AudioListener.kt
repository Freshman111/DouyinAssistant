package com.sgffsg.douyin.assist.recorder

/**
 * Created by xuyang on 2021/1/20.
 */
interface AudioListener {

    fun onStart()

    fun onAudioFrame(chunk: AudioChunk)

    fun onStopAndRelease()

    fun onError(exception: Exception)

}