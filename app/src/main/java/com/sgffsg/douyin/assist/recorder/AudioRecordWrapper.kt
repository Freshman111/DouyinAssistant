package com.sgffsg.douyin.assist.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.OnRecordPositionUpdateListener
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * AudioRecord包装类，支持帧数据回调
 * Created by xuyang on 2021/1/20.
 */
@SuppressLint("MissingPermission")
class AudioRecordWrapper @JvmOverloads constructor(
    //listener
    listener: AudioListener,
    //录音设置，默认PCM_16K
    private val audioType: AudioType = AudioType.PCM_16K,
    //设置声道，默认单声道
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    //声道数，默认1
    private val channelCount: Int = 1,
    //每帧缓存时间，根据此参数计算音频帧大小
    private val bufferLengthInMs: Int = PCM_BUFFER_LENGTH_MILLS
) {
    private val TAG = "AudioRecordWrapper"

    private val periodicHandler: Handler by lazy {
        Handler(Looper.myLooper() ?: Looper.getMainLooper())
    }
    private val bufferSize: Int by lazy {
        getBufferSizeInBytes()
    }
    private val recorder: AudioRecord by lazy {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            audioType.sampleRateInHz,
            channelConfig,
            audioType.audioFormat,
            bufferSize
        )
    }

    //回调监听（未直接用构造方法中listener，因后续设置为空，更好处理逻辑）
    private var realListener: AudioListener? = listener

    @Synchronized
    fun start() {
        if (realListener == null) return
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            realListener?.onError(RuntimeException("AudioRecord初始化错误"))
            cancelAndCleanup()
            return
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            //待办，SDK中不应该有平台相关的代码，暂时不影响，后续重构
//            AECUtil.setPlusEnableAEC(true)
            realListener?.onStart()
            //读取音频帧监听
            recorder.setRecordPositionUpdateListener(readBufferListener)
            //开始录音
            recorder.startRecording()
            //触发首次读取
            readBufferListener.onMarkerReached(recorder)
        }
    }

    @Synchronized
    fun stopAndRelease() {
        if (realListener == null) return
        //读取最后音频帧
        periodicHandler.removeCallbacks(periodicReadRunnable)
        val chunk = readAudioChunk()
        if (chunk != null) {
            realListener?.onAudioFrame(chunk)
        }
        realListener?.onStopAndRelease()
        cancelAndCleanup()
    }

    @Synchronized
    fun cancelAndCleanup() {
        Log.d(TAG, "cancelAndCleanup")
        realListener = null
        if (recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.stop()
        }
        recorder.release()
    }

    /**
     * 定时读取音频帧，因某些机型setRecordPositionUpdateListener可能不会回调
     */
    private val periodicReadRunnable = {
        readBufferListener.onMarkerReached(recorder)
    }

    private val readBufferListener: OnRecordPositionUpdateListener =
        object : OnRecordPositionUpdateListener {
            override fun onMarkerReached(recorder: AudioRecord) {
                if (realListener == null) return
                //每次读取音频帧时，删除定时读取
                periodicHandler.removeCallbacks(periodicReadRunnable)
                //再次设置音频帧
                recorder.notificationMarkerPosition = bufferSize
                val chunk = readAudioChunk()
                if (chunk != null) {
                    realListener?.onAudioFrame(chunk)
                }
                //定时读取，因某些机型setRecordPositionUpdateListener可能不会回调
                periodicHandler.postDelayed(periodicReadRunnable, bufferLengthInMs.toLong())
            }

            override fun onPeriodicNotification(recorder: AudioRecord) {
            }
        }

    private fun readAudioChunk(): AudioChunk? {
        if (realListener == null) return null
        var lenRead = 0
        var toRead: Int = bufferSize
        var buffer = ByteArray(bufferSize)
        do {
            val bytesRead = recorder.read(buffer, lenRead, toRead)
            if (bytesRead < 0) break
            lenRead += bytesRead
            toRead -= bytesRead
        } while (toRead > 0)
        if (lenRead <= 0) {
            realListener?.onError(RuntimeException("AudioRecord#read $lenRead"))
            cancelAndCleanup()
            return null
        }
        if (lenRead < bufferSize) {
            buffer = createSubArray(buffer, 0, lenRead)
        }
        return AudioChunk(audioType, buffer)
    }

    private fun getBufferSizeInBytes(): Int {
        //根据每帧缓存时间计算size
        val preferredSizeInBytes =
            audioType.sampleSizeInBytes * audioType.sampleRateInHz * bufferLengthInMs * channelCount / 1000
        //最小size
        val minSizeInBytes =
            AudioRecord.getMinBufferSize(
                audioType.sampleRateInHz,
                channelConfig,
                audioType.audioFormat
            )
        return if (minSizeInBytes != AudioRecord.ERROR_BAD_VALUE
            && minSizeInBytes != AudioRecord.ERROR
            && minSizeInBytes > preferredSizeInBytes
        ) {
            minSizeInBytes
        } else {
            preferredSizeInBytes
        }
    }

    private fun createSubArray(input: ByteArray, offset: Int, length: Int): ByteArray {
        var realLength = length
        if (offset + realLength > input.size) {
            realLength = input.size - offset
        }
        val ret = ByteArray(realLength)
        System.arraycopy(input, offset, ret, 0, realLength)
        return ret
    }

}

const val PCM_BUFFER_LENGTH_MILLS = 200