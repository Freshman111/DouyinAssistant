package com.sgffsg.douyin.assist.kws

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.AtomicFile
import com.sgffsg.douyin.assist.common.utils.LogUtil
import androidx.annotation.RequiresPermission
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records the 10 s voice sample that VoxLock uses to keep the user's voice and
 * drop the rest. The sample decides how well the separator works, so the
 * capture is checked for length, level and clipping before it is saved.
 */
object VoiceEnrollment {
    internal const val SAMPLE_RATE = 16_000
    private const val SAMPLE_COUNT = SAMPLE_RATE * 10
    private const val HEADER_BYTES = 44
    private const val FRAME_SIZE = SAMPLE_RATE / 50
    private const val TAG = "VoiceEnrollment"
    private const val MIN_AUDIBLE_SECONDS = 1.5f
    private const val MIN_PEAK_DB = -50f
    private const val MAX_CLIPPED_RATIO = 0.01f
    private const val NOISE_PERCENTILE = 0.1
    private const val SPEECH_OVER_NOISE_DB = 10.0
    private const val FLOOR_DB = -60.0

    data class Result(
        val ok: Boolean,
        val message: String,
        val audibleSeconds: Float,
        val peakDb: Float,
        val noiseDb: Float,
    )

    internal fun sampleFile(context: Context) =
        File(context.noBackupFilesDir, "voice-experiment-reference.wav")

    fun hasSample(context: Context): Boolean =
        sampleFile(context).length() == (HEADER_BYTES + SAMPLE_COUNT * 2).toLong()

    fun sampleModifiedAt(context: Context): Long = sampleFile(context).lastModified()

    fun hasModels(context: Context): Boolean =
        context.assets.list("voice-experiment")?.asList()?.containsAll(
            listOf("enroll.onnx", "chunk_int8.onnx", "paraformer.int8.onnx", "tokens.txt")
        ) == true

    fun deleteSample(context: Context) {
        sampleFile(context).delete()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun record(context: Context, onProgress: (Float, Float) -> Unit): Result {
        val format = AudioFormat.ENCODING_PCM_16BIT
        val channel = AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, format)
        check(minBuffer > 0) { "设备不支持当前录音格式" }
        val microphone = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, channel, format, minBuffer * 2
        )
        val samples = ShortArray(SAMPLE_COUNT)
        try {
            check(microphone.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            microphone.startRecording()
            check(microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风无法开始录音" }
            var written = 0
            while (written < samples.size) {
                check(!Thread.currentThread().isInterrupted) { "录音已取消" }
                val count = microphone.read(samples, written, minOf(FRAME_SIZE, samples.size - written))
                check(count > 0) { "麦克风读取失败，请关闭语音控制后重试" }
                written += count
                var power = 0.0
                for (i in written - count until written) {
                    val value = samples[i].toDouble()
                    power += value * value
                }
                val rms = sqrt(power / count)
                onProgress(written.toFloat() / samples.size, levelOf(rms))
            }
        } finally {
            microphone.release()
        }
        check(!Thread.currentThread().isInterrupted) { "录音已取消" }

        val frameCount = samples.size / FRAME_SIZE
        val frameDb = DoubleArray(frameCount)
        var clipped = 0
        var peak = 0
        for (frame in 0 until frameCount) {
            var power = 0.0
            val start = frame * FRAME_SIZE
            for (i in start until start + FRAME_SIZE) {
                val value = samples[i].toInt()
                power += value.toDouble() * value
                val magnitude = if (value < 0) -value else value
                if (magnitude > peak) peak = magnitude
                if (magnitude >= 32700) clipped++
            }
            val rms = sqrt(power / FRAME_SIZE) / 32768.0
            frameDb[frame] = 20.0 * log10(max(rms, 1e-7))
        }
        val noiseDb = percentile(frameDb, NOISE_PERCENTILE)
        // 以环境噪声为基准判断有没有说话，避免手机麦克风偏小就把人判成没说话。
        val speechDb = max(noiseDb + SPEECH_OVER_NOISE_DB, FLOOR_DB)
        val audibleFrames = frameDb.count { it > speechDb }
        val audibleSeconds = audibleFrames / 50f
        val peakDb = (20.0 * log10(max(peak / 32768.0, 1e-7))).toFloat()
        val clippedRatio = clipped.toFloat() / samples.size

        val message = when {
            audibleSeconds < MIN_AUDIBLE_SECONDS -> "有效语音只有 %.1f 秒，请把 5 个词都说完".format(audibleSeconds)
            peakDb < MIN_PEAK_DB -> "声音太轻，请靠近手机或提高音量"
            clippedRatio > MAX_CLIPPED_RATIO -> "声音太大，离手机远一点再录"
            else -> "录入完成"
        }
        val ok = audibleSeconds >= MIN_AUDIBLE_SECONDS &&
            peakDb >= MIN_PEAK_DB && clippedRatio <= MAX_CLIPPED_RATIO
        // 不论成功与否都留一份，下次判定异常时可以直接看这段音频。
        save(context, samples, lastCaptureFile(context))
        if (ok) save(context, samples, sampleFile(context))
        LogUtil.i(
            TAG,
            "record ok=$ok audible_s=${"%.1f".format(audibleSeconds)} peak_db=${"%.0f".format(peakDb)} " +
                "noise_db=${"%.0f".format(noiseDb)} threshold_db=${"%.0f".format(speechDb)}"
        )
        return Result(ok, message, audibleSeconds, peakDb, noiseDb.toFloat())
    }

    private fun levelOf(rms: Double): Float {
        val db = 20.0 * log10(max(rms, 1e-6))
        return ((db + 60.0) / 50.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun percentile(values: DoubleArray, fraction: Double): Double {
        val copy = values.copyOf()
        copy.sort()
        val index = ((copy.size - 1) * fraction).toInt().coerceIn(0, copy.size - 1)
        return copy[index]
    }

    internal fun lastCaptureFile(context: Context) =
        File(context.noBackupFilesDir, "voice-experiment-last.wav")

    private fun save(context: Context, samples: ShortArray, target: File) {
        val pcmBytes = samples.size * 2
        val wav = ByteBuffer.allocate(HEADER_BYTES + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + pcmBytes)
        wav.put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        wav.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(pcmBytes)
        wav.asShortBuffer().put(samples)
        val file = AtomicFile(target)
        val output = file.startWrite()
        try {
            output.write(wav.array())
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
