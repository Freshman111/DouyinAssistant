package com.sgffsg.douyin.assist.audio.record

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File

/**
 * @author: lmk
 * @date: 2024/5/7
 * @desc: 录音库外观类
 */
class AudioRecorder private constructor(
    var context: Context,
    var recorderType: RecorderType?,
    var audioSource: Int,
    var sampleRate: Int,
    var channelConfig: Int,
    var audioFormat: Int,
    var minBufferSize: Int,
    var audioFile: File?
) {
    var mediaRecorder: MediaRecorder? = null
    var audioRecord: AudioRecord? = null

    private constructor(builder: Builder) : this(
        builder.context,
        builder.recorderType,
        builder.audioSource,
        builder.sampleRate,
        builder.channelConfig,
        builder.audioFormat,
        builder.minBufferSize,
        builder.audioFile
    )

    init {
        prepare()
    }

    companion object {
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        lateinit var context: Context

        /**
         * 录音器类型，AudioRecord或者MediaRecord
         */
        var recorderType: RecorderType? = null

        /**
         * 录音声源
         */
        var audioSource: Int = MediaRecorder.AudioSource.MIC

        /**
         * 采样率
         */
        var sampleRate: Int = 44100

        /**
         * 录音channel，单声道还是双声道
         */
        var channelConfig: Int = AudioFormat.CHANNEL_IN_MONO

        /**
         * 设置格式，安卓手机都支持的是PCM16
         */
        var audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT

        /**
         * 计算AudioRecord内部buffer大小
         */
        var minBufferSize: Int =
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        /**
         * 录音文件存储位置
         */
        var audioFile: File? = null

        fun build() = AudioRecorder(this)
    }

    @SuppressLint("MissingPermission")
    private fun prepare() {
//        if (audioFile == null){
//            audioFile =
//        }
        if (recorderType == RecorderType.AUDIO_RECORD) {
            mediaRecorder = null
            audioRecord =
                AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, minBufferSize)
        } else if (recorderType == RecorderType.MEDIA_RECORD) {
            audioRecord = null
            mediaRecorder = MediaRecorder()
            mediaRecorder?.run {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }

        }
    }

}