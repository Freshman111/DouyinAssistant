package com.sgffsg.douyin.assist.core.asr

import android.content.Context
import android.util.Log
import com.sgffsg.douyin.assist.util.LogUtil
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import java.io.IOException
import java.util.Arrays

/**
 * 语音识别服务
 * 提取自VoskActivity，用于在无障碍服务中使用
 */
class VoiceRecognitionService(
    private val context: Context
) : RecognitionListener {

    private val TAG = "VoiceRecognitionService"
    
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var listener: VoiceCommandListener? = null
    private var isInitialized = false
    private var isListening = false

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val MODEL_NAME = "vosk-model-small-cn"
//        private val PHRASES_LIST = listOf("上滑", "向上滑", "下一个", "下一条","下滑",
//            "向下滑", "上一个", "上一条","下一页", "翻页","上一页", "返回上页","跳过", "下一个",
//            "点赞", "喜欢", "双击","返回", "退出", "回去")

        private val PHRASES_LIST = """
            [
                "上滑", "向上滑", "翻页", "跳过",
                "上一个", "上一页", "上一条", "返回上页",
                "点赞", "喜欢", "双击",
                "返回", "退出", "回去"
            ]
        """.trimIndent()
    }

    init {
        LibVosk.setLogLevel(LogLevel.INFO)
    }

    /**
     * 初始化语音识别模型
     */
    fun initialize(callback: (Boolean, String?) -> Unit) {
        if (isInitialized) {
            callback(true, null)
            return
        }

        LogUtil.d(TAG, "开始初始化语音识别模型")
        
        StorageService.unpack(
            context, 
            MODEL_NAME, 
            "model",
            { loadedModel ->
                this.model = loadedModel
                isInitialized = true
                LogUtil.d(TAG, "语音识别模型初始化成功")
                callback(true, null)
            },
            { exception ->
                val errorMsg = "模型初始化失败: ${exception.message}"
                Log.e(TAG, errorMsg, exception)
                callback(false, errorMsg)
            }
        )
    }

    /**
     * 设置语音指令监听器
     */
    fun setVoiceCommandListener(listener: VoiceCommandListener?) {
        this.listener = listener
    }

    /**
     * 开始语音识别
     */
    fun startListening(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "模型未初始化，无法开始识别")
            listener?.onError("模型未初始化")
            return false
        }

        if (isListening) {
            Log.w(TAG, "已在监听中")
            return true
        }

        return try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(this)
            isListening = true
            listener?.onStateChanged(true)
            LogUtil.d(TAG, "开始语音识别")
            true
        } catch (e: IOException) {
            val errorMsg = "启动识别失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            listener?.onError(errorMsg)
            false
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) {
            return
        }

        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        isListening = false
        listener?.onStateChanged(false)
        LogUtil.d(TAG, "停止语音识别")
    }

    /**
     * 暂停/恢复识别
     */
    fun setPause(pause: Boolean) {
        speechService?.setPause(pause)
        LogUtil.d(TAG, if (pause) "暂停识别" else "恢复识别")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        model?.close()
        model = null
        isInitialized = false
        LogUtil.d(TAG, "释放语音识别资源")
    }

    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean = isListening

    // RecognitionListener 实现

    override fun onResult(hypothesis: String?) {
        LogUtil.d(TAG, "识别结果: $hypothesis")
        parseAndExecuteCommand(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        LogUtil.d(TAG, "最终结果: $hypothesis")
        parseAndExecuteCommand(hypothesis)
    }

    override fun onPartialResult(hypothesis: String?) {
        LogUtil.d(TAG, "部分结果: $hypothesis")
        // 部分结果可以用于实时反馈，但不执行指令
        parseAndExecuteCommand(hypothesis)
    }

    override fun onError(exception: Exception?) {
        val errorMsg = "识别错误: ${exception?.message}"
        Log.e(TAG, errorMsg, exception)
        listener?.onError(errorMsg)
    }

    override fun onTimeout() {
        LogUtil.d(TAG, "识别超时")
    }

    /**
     * 解析识别结果并执行指令
     */
    private fun parseAndExecuteCommand(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) {
            return
        }
//        LogUtil.d(TAG, "parseAndExecuteCommand: $hypothesis")
        try {
            // Vosk返回的是JSON格式
            val jsonResult = JSONObject(hypothesis)
            val text = jsonResult.optString("text", "")
            
            if (text.isBlank()) {
                return
            }

            LogUtil.d(TAG, "识别文本: $text")

            // 匹配语音指令
            val command = VoiceCommand.fromText(text)
            if (command != null) {
                LogUtil.d(TAG, "匹配到指令: ${command.name}")
                listener?.onCommandRecognized(command, text)
            } else {
                LogUtil.d(TAG, "未匹配到有效指令: $text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析识别结果失败: ${e.message}", e)
        }
    }
}
