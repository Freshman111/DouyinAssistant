package com.sgffsg.douyin.assist.kws

import android.content.Context
import com.sgffsg.douyin.assist.common.utils.LogUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class PcmPosition(val fileName: String, val samplesWritten: Long)

/**
 * 音频录制和保存工具类
 */
class AudioRecorder {
    private val TAG = "AudioRecorder"

    private val lock = ReentrantLock() // 线程安全锁
    private var outputStream: FileOutputStream? = null
    private var outputFile: File? = null
    private var samplesWritten = 0L
    // 标记是否正在录制，volatile保证可见性
    @Volatile
    private var isRecording = false

    /**
     * 开始录制音频
     */
    fun startRecording(context: Context): Boolean {
        return lock.withLock {
            if (isRecording) return@withLock false
            try {
                // 创建输出文件
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
                val fileName = "audio_recording_$timeStamp.pcm"
                val outputFile = File(getExternalFilesDir(context), fileName)
                outputStream = FileOutputStream(outputFile)
                this.outputFile = outputFile
                samplesWritten = 0
                LogUtil.i(TAG, "startRecording path:${outputFile.path}")
                isRecording = true
                true
            } catch (e: IOException) {
                LogUtil.e(TAG, "Failed to start PCM recording", e)
                false
            }
        }

    }

    /**
     * 写入音频数据
     */
    internal fun writeAudioData(data: ShortArray, length: Int): PcmPosition? {
        return lock.withLock {
            val output = outputStream ?: return@withLock null
            val file = outputFile ?: return@withLock null
            if (!isRecording || length <= 0 || length > data.size) {
                return@withLock null
            }

            try {
                // 优化：批量转换Short数组为字节数组（替代循环putShort）
                val byteBuffer = ByteBuffer.allocate(length * 2)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                // 直接包装数组，批量写入，避免循环
                byteBuffer.asShortBuffer().put(data, 0, length)

                val byteArray = byteBuffer.array()
                output.write(byteArray, 0, length * 2)
                samplesWritten += length
                return@withLock PcmPosition(file.name, samplesWritten)
            } catch (e: IOException) {
                LogUtil.e(TAG, "Failed to write PCM recording", e)
                // 写入失败时停止录制，避免持续错误
                stopRecording()
                return@withLock null
            }
        }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        lock.withLock {
            if (!isRecording) return@withLock
            isRecording = false
            try {
                outputStream?.close()
            } catch (e: IOException) {
                LogUtil.e(TAG, "Failed to close PCM recording", e)
            } finally {
                outputStream = null
                outputFile = null
            }
        }
    }

    /**
     * 获取外部文件目录
     */
    private fun getExternalFilesDir(context: Context): File {
        // 返回应用私有目录下的Audio文件夹
        val dir = File(context.externalCacheDir, "Audio")
        if (!dir.exists()) {
            val res = dir.mkdirs()
            LogUtil.i(TAG, "getExternalFilesDir res = $res, dir = ${dir.path}")
        }
        return dir
    }

    /**
     * 检查是否正在录制
     */
    fun isRecording(): Boolean {
        return isRecording
    }
}
