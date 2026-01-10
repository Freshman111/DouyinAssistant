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
    private var isListening = false
    private lateinit var kws: KeywordSpotter
    private lateinit var stream: OnlineStream

    //------ record start
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    //------ record end

    private var listener: CommandListener? = null

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
    fun startListening(): Boolean {
        if (!isInitialized) {
            LogUtil.e(TAG, "模型未初始化，无法开始识别")
            listener?.onError("模型未初始化")
            return false
        }

        if (isListening) {
            LogUtil.w(TAG, "已在监听中")
            return true
        }
        val ret = initMicrophone()
        if (!ret) {
            LogUtil.e(TAG, "Failed to initialize microphone")
            return false
        }
        LogUtil.e(TAG, "state: ${audioRecord?.state}")
        audioRecord?.startRecording()
        isListening = true

        LogUtil.e(TAG,  "Created stream. Running ...")

        var keywords = ""
        Log.i(TAG, "Raw keywords: $keywords")

        keywords = keywords.replace("\n", "/")
        keywords = keywords.trim()

        Log.i(TAG, "Normalized keywords: $keywords")

        stream = kws.createStream(keywords)

        recordingThread = thread(true) {
            processData()
        }

        LogUtil.e(TAG, "Started recording")
        return true
    }

    private fun processData() {
        LogUtil.i(TAG, "processing samples")

        val interval = 0.1 // i.e., 100 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)

        while (isListening) {
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            if (ret != null && ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                while (kws.isReady(stream)) {
                    kws.decode(stream)

                    val text = kws.getResult(stream).keyword

                    if (text.isNotBlank()) {
                        // Remember to reset the stream right after detecting a keyword
                        kws.reset(stream)
                    }
                    parseAndExecuteCommand(text)
                }
            }
        }

        stream.release()
        LogUtil.i(TAG, "Released stream. Stopped")

        audioRecord?.let {
            it.stop()
            it.release()
        }

        audioRecord = null
    }

    private fun initMicrophone(): Boolean {

        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        LogUtil.i(TAG, "buffer size in milliseconds: ${numBytes * 1000.0f / sampleRateInHz}")

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtil.e(TAG, "无录音权限")
            return false
        }
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )
        return true
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) {
            return
        }
        try {
            // 确保处于录制状态
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // 释放资源
            audioRecord?.release()
            audioRecord = null
        }
        isListening = false
        LogUtil.i(TAG, "停止语音识别")
    }

    /**
     * 暂停/恢复识别
     */
    fun setPause(pause: Boolean) {
        if (pause) {
            audioRecord?.stop()
        } else {
            audioRecord?.startRecording()
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
    private fun parseAndExecuteCommand(text: String?) {
        if (text.isNullOrBlank()) {
            return
        }
        LogUtil.i(TAG, "parseAndExecuteCommand: $text")
        try {
//            AsrDispatcher.getInstance().onAsrRecognized(text)
            // 匹配语音指令
            val command = Command.fromText(text)
            if (command != null) {
//                LogUtil.i(TAG, "匹配到指令: ${command.name}")
                listener?.onCommandRecognized(command, text)
            } else {
//                LogUtil.d(TAG, "未匹配到有效指令: $text")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析识别结果失败: ${e.message}", e)
        }
    }
}
