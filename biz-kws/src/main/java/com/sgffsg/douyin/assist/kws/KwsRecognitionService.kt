package com.sgffsg.douyin.assist.kws

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKeywordsFile
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import com.sgffsg.douyin.assist.common.utils.DebugCache
import com.sgffsg.douyin.assist.common.utils.LogUtil
import com.sgffsg.douyin.assist.kws.listener.CommandListener
import kotlin.concurrent.thread

/**
 * 语音识别服务
 * 提取自VoskActivity，用于在无障碍服务中使用
 */
class KwsRecognitionService(
    private val context: Context
) {

    private val TAG = "VoiceRecognitionService"
    
    private var isInitialized = false
    @Volatile
    private var isListening = false
    private lateinit var kws: KeywordSpotter

    //------ record start
    private val recordingLock = Any()
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    //------ record end

    private var listener: CommandListener? = null
    
    // 音频录制保存功能
    private val audioRecorder = AudioRecorder()

    /**
     * 初始化语音识别模型
     */
    fun initialize(callback: (Boolean, String?) -> Unit) {
        if (isInitialized) {
            callback(true, null)
            return
        }

        LogUtil.i(TAG, "Start to initialize model")
        initModel()
        LogUtil.i(TAG, "Finished initializing model")
    }

    /**
     * 获取音频保存状态
     */
    fun isSaveAudioEnabled(): Boolean {
        return DebugCache.isSaveAudioEnabled()
    }

    /**
     * 设置语音指令监听器
     */
    fun setVoiceCommandListener(listener: CommandListener?) {
        this.listener = listener
    }


    private fun initModel() {
        // Please change getKwsModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
        // for a list of available models
        val type = 0
        LogUtil.i(TAG, "Select model type $type")
        val config = KeywordSpotterConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getKwsModelConfig(type = type)!!,
            keywordsFile = getKeywordsFile(type = type),
        )

        kws = KeywordSpotter(
            assetManager = context.assets,
            config = config,
        )
        isInitialized = true
    }

    /**
     * 开始语音识别
     */
    fun startListening(): Boolean = synchronized(recordingLock) {
        if (!isInitialized) {
            LogUtil.e(TAG, "模型未初始化，无法开始识别")
            listener?.onError("模型未初始化")
            return false
        }

        if (isListening) {
            LogUtil.w(TAG, "已在监听中")
            return true
        }
        if (recordingThread?.isAlive == true) {
            LogUtil.w(TAG, "上一段录音正在结束")
            return false
        }
        val audioSource = if (DebugCache.useVoiceRecognitionSource()) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        val record = startMicrophone(audioSource) ?: return false
        audioRecord = record
        startAudioFile()
        
        isListening = true

        LogUtil.e(TAG,  "Created stream. Running ...")

        val keywords = ""
        Log.i(TAG, "Raw keywords: $keywords")

        var normalizedKeywords = keywords.replace("\n", "/")
        normalizedKeywords = normalizedKeywords.trim()

        Log.i(TAG, "Normalized keywords: $normalizedKeywords")

        val stream = kws.createStream(normalizedKeywords)

        recordingThread = thread(name = "KwsRecording") {
            processData(record, stream)
        }

        LogUtil.e(TAG, "Started recording")
        true
    }

    private fun startAudioFile() {
        if (isSaveAudioEnabled()) audioRecorder.startRecording(context)
    }

    private fun processData(record: AudioRecord, stream: OnlineStream) {
        LogUtil.i(TAG, "processing samples")

        val interval = 0.1 // i.e., 100 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)
        var diagnostics: KwsDiagnostics? = null

        try {
            while (isListening) {
                diagnostics = if (LogUtil.isFileLoggingEnabled()) {
                    diagnostics ?: KwsDiagnostics(record.audioSessionId, sampleRateInHz, record.audioSource)
                } else {
                    null
                }
                val readStarted = if (diagnostics != null) System.nanoTime() else 0L
                val ret = record.read(buffer, 0, buffer.size)
                if (!isListening) break
                val readFinished = if (diagnostics != null) System.nanoTime() else 0L
                val saveAudio = ret > 0 && isSaveAudioEnabled() && audioRecorder.isRecording()
                val pcmPosition = if (saveAudio) audioRecorder.writeAudioData(buffer, ret) else null
                if (diagnostics != null) {
                    diagnostics.recordRead(
                        buffer, ret, pcmPosition, readFinished - readStarted,
                        if (saveAudio) System.nanoTime() - readFinished else 0L
                    )
                }
                val kwsStarted = if (diagnostics != null) System.nanoTime() else 0L
                if (ret <= 0) {
                    diagnostics?.recordProcessing(0)
                    continue
                }

                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                while (kws.isReady(stream)) {
                    kws.decode(stream)

                    val text = kws.getResult(stream).keyword

                    if (text.isNotBlank()) {
                        // Remember to reset the stream right after detecting a keyword
                        kws.reset(stream)
                    }
                    parseAndExecuteCommand(text, diagnostics)
                }
                diagnostics?.recordProcessing(System.nanoTime() - kwsStarted)
            }
        } finally {
            try {
                diagnostics?.finish()
                stream.release()
            } finally {
                synchronized(recordingLock) {
                    isListening = false
                    try {
                        record.release()
                    } finally {
                        audioRecord = null
                        audioRecorder.stopRecording()
                    }
                }
            }
            LogUtil.i(TAG, "Released stream. Stopped")
        }
    }

    private fun startMicrophone(audioSource: Int): AudioRecord? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtil.e(TAG, "无录音权限")
            return null
        }
        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val record = try {
            AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, numBytes * 2)
        } catch (e: RuntimeException) {
            LogUtil.e(TAG, "Failed to initialize microphone source=$audioSource", e)
            return null
        }
        try {
            record.startRecording()
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) return record
            LogUtil.e(TAG, "Microphone did not start source=$audioSource")
        } catch (e: RuntimeException) {
            LogUtil.e(TAG, "Failed to start microphone source=$audioSource", e)
        }
        record.release()
        return null
    }

    /**
     * 停止语音识别
     */
    fun stopListening() = synchronized(recordingLock) {
        if (!isListening) {
            return
        }
        val record = audioRecord
        isListening = false
        try {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to stop microphone", e)
        }
        LogUtil.i(TAG, "停止语音识别")
    }

    /**
     * 暂停/恢复识别
     */
    fun setPause(pause: Boolean) = synchronized(recordingLock) {
        if (!isListening) return
        val record = audioRecord ?: return
        if (pause) {
            record.stop()
            audioRecorder.stopRecording()
        } else {
            record.startRecording()
            startAudioFile()
        }
        LogUtil.d(TAG, if (pause) "暂停识别" else "恢复识别")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        isInitialized = false
        LogUtil.d(TAG, "释放语音识别资源")
    }

    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean = isListening

    /**
     * 解析识别结果并执行指令
     */
    private fun parseAndExecuteCommand(text: String, diagnostics: KwsDiagnostics?) {
        if (text.isBlank()) {
            return
        }
        LogUtil.i(TAG, "parseAndExecuteCommand: $text")
        try {
            val command = Command.fromText(text)
            val logSwipe = diagnostics?.hit(text, command)
            if (command != null) {
                listener?.onCommandRecognized(command, text, logSwipe)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析识别结果失败: ${e.message}", e)
        }
    }
}
