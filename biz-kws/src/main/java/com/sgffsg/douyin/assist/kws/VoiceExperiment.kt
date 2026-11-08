package com.sgffsg.douyin.assist.kws

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserVoxLockModelConfig
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiserConfig
import com.sgffsg.douyin.assist.common.utils.LogUtil
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Candidate-only VoxLock + Paraformer. Only parts of the audio that stand out
 * from the local background go through the denoiser; silence is fed to the ASR
 * windows as zeros. This keeps the denoiser away from full-time playback, which
 * costs more than real time on the phone.
 */
internal class VoiceExperiment(
    private val context: Context,
    private val sessionId: Int,
    private val onCommand: ((String) -> Unit)? = null,
) {
    private data class Frame(val samples: FloatArray, val endSample: Long, val capturedAt: Long)
    private data class Gated(val frame: Frame, val speech: BooleanArray)

    private val frames = ArrayBlockingQueue<Frame>(50)
    @Volatile private var closing = false
    @Volatile private var failed = false
    private var inputSamples = 0L
    private var denoisedSamples = 0L
    private var segments = 0
    private val worker = thread(name = "VoxLockParaformer") {
        try {
            run()
        } catch (error: Exception) {
            failed = true
            LogUtil.e(TAG, "error session_id=$sessionId", error)
        } catch (error: LinkageError) {
            failed = true
            LogUtil.e(TAG, "jni_error session_id=$sessionId", error)
        } finally {
            frames.clear()
            LogUtil.i(
                TAG, "stop session_id=$sessionId failed=$failed segments=$segments " +
                    "audio_ms=${inputSamples * 1000 / RATE} denoised_ms=${denoisedSamples * 1000 / RATE}"
            )
        }
    }

    fun offer(samples: FloatArray, capturedAt: Long) {
        if (failed) return
        inputSamples += samples.size
        if (!frames.offer(Frame(samples, inputSamples, capturedAt))) {
            // A gap would invalidate denoiser state and PCM offsets for this session.
            failed = true
            LogUtil.e(TAG, "overrun session_id=$sessionId input_samples=$inputSamples")
        }
    }

    fun close() {
        closing = true
        worker.join()
    }

    private fun modelFile(name: String): File {
        val directory = File(context.noBackupFilesDir, "voice-experiment-v1")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create model directory" }
        val file = File(directory, name)
        if (file.length() > 0) return file
        val temp = File.createTempFile("model-", ".tmp", directory)
        try {
            context.assets.open("voice-experiment/$name").use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            check(temp.renameTo(file)) { "Cannot save experiment model $name" }
        } finally {
            temp.delete()
        }
        return file
    }

    private fun run() {
        val start = System.nanoTime()
        val denoiser = OnlineSpeechDenoiser(
            config = OnlineSpeechDenoiserConfig(
                OfflineSpeechDenoiserModelConfig(
                    voxlock = OfflineSpeechDenoiserVoxLockModelConfig(
                        enrollModel = modelFile("enroll.onnx").path,
                        chunkModel = modelFile("chunk_int8.onnx").path,
                        voiceSample = VoiceEnrollment.sampleFile(context).path
                    ),
                    numThreads = 2
                )
            )
        )
        try {
            val recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        paraformer = OfflineParaformerModelConfig("voice-experiment/paraformer.int8.onnx"),
                        tokens = "voice-experiment/tokens.txt"
                    )
                )
            )
            try {
                LogUtil.i(TAG, "ready session_id=$sessionId init_ms=${elapsedMs(start)}")
                process(denoiser, recognizer)
            } finally {
                recognizer.release()
            }
        } finally {
            denoiser.release()
        }
    }

    private fun process(denoiser: OnlineSpeechDenoiser, recognizer: OfflineRecognizer) {
        val window = FloatArray(WINDOW)
        var filled = 0
        var outputSamples = 0L
        var lastHitEnd = 0L
        var lastFrame: Frame? = null
        var denoiseMs = 0L
        var active = false
        var silentFrames = 0
        var denoiseCalls = 0
        var segmentSamples = 0L
        var cooldown = 0L
        var skipped = 0

        val pending = ArrayDeque<Gated>()
        val history = FloatArray(HISTORY)
        var historyCount = 0
        var historyNext = 0

        fun consume(samples: FloatArray, frame: Frame) {
            var offset = 0
            while (offset < samples.size && !failed) {
                val count = minOf(samples.size - offset, window.size - filled)
                samples.copyInto(window, filled, offset, offset + count)
                filled += count
                offset += count
                outputSamples += count
                if (filled < window.size) continue

                var energy = 0.0
                for (value in window) energy += value.toDouble() * value
                val start = System.nanoTime()
                val text = if (energy / window.size < SILENCE) {
                    ""
                } else {
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(window, RATE)
                        recognizer.decode(stream)
                        recognizer.getResult(stream).text
                    } finally {
                        stream.release()
                    }
                }
                val asrMs = elapsedMs(start)
                val startSample = outputSamples - WINDOW
                val matched = Command.isSkipPhrase(text)
                val emitted = matched && startSample >= lastHitEnd
                if (emitted) {
                    lastHitEnd = outputSamples
                    onCommand?.invoke(TARGET)
                }
                val lagMs = elapsedMs(frame.capturedAt) + (frame.endSample - outputSamples) * 1000 / RATE
                LogUtil.i(
                    TAG, "window session_id=$sessionId start_sample=$startSample end_sample=$outputSamples " +
                        "asr_ms=$asrMs denoise_ms=$denoiseMs lag_ms=$lagMs queue=${frames.size} " +
                        "matched=$matched emitted=$emitted text=$text"
                )
                denoiseMs = 0
                window.copyInto(window, endIndex = WINDOW, startIndex = STEP)
                filled -= STEP
            }
        }

        fun classify(samples: FloatArray): BooleanArray {
            val count = max(1, (samples.size + SUBFRAME - 1) / SUBFRAME)
            val speech = BooleanArray(count)
            for (index in 0 until count) {
                val begin = index * SUBFRAME
                val end = minOf(begin + SUBFRAME, samples.size)
                if (begin >= end) continue
                var sum = 0.0
                for (i in begin until end) {
                    val value = samples[i].toDouble()
                    sum += value * value
                }
                val rms = sqrt(sum / (end - begin))
                val db = (20.0 * log10(max(rms, 1e-7))).toFloat()
                history[historyNext] = db
                historyNext = (historyNext + 1) % HISTORY
                historyCount = minOf(historyCount + 1, HISTORY)
                val median = median(history, historyCount)
                speech[index] = rms > FLOOR && db > median + 6.0f
            }
            return speech
        }

        fun finish(item: Gated, reason: String) {
            val tail = denoiser.flush().samples
            denoisedSamples += tail.size
            consume(tail, item.frame)
            active = false
            silentFrames = 0
            LogUtil.i(
                TAG, "segment session_id=$sessionId reason=$reason samples=$segmentSamples " +
                    "queue=${frames.size} skipped=$skipped"
            )
            segmentSamples = 0
        }

        fun feed(item: Gated) {
            val speech = item.speech.any { it }
            val behind = frames.size > QUEUE_HIGH
            if (active && behind) {
                // Playback is louder and denser than the denoiser can follow: drop this
                // candidate instead of letting the queue grow until it overruns.
                skipped++
                finish(item, "backlog")
                cooldown = outputSamples + RATE
                consume(FloatArray(item.frame.samples.size), item.frame)
                return
            }
            // Require about 120 ms of speech inside the look-ahead window, matching the
            // offline gate. Single blips otherwise open candidates that cost more than
            // they can ever pay back.
            val speechInWindow = item.speech.count { it } +
                pending.sumOf { gated -> gated.speech.count { it } }
            if (!active && outputSamples >= cooldown && speechInWindow >= MIN_SPEECH) {
                active = true
                silentFrames = 0
                segmentSamples = 0
                denoiser.reset()
                segments++
            }
            val output = if (active) {
                val start = System.nanoTime()
                val scaled = FloatArray(item.frame.samples.size) { item.frame.samples[it] * GAIN }
                val samples = denoiser.run(scaled, RATE).samples
                denoiseMs += elapsedMs(start)
                denoisedSamples += samples.size
                denoiseCalls++
                segmentSamples += samples.size
                samples
            } else {
                FloatArray(item.frame.samples.size)
            }
            consume(output, item.frame)
            if (!active) return
            silentFrames = if (speech) 0 else silentFrames + 1
            if (segmentSamples >= MAX_SEGMENT) {
                finish(item, "length")
                cooldown = outputSamples + RATE
                return
            }
            if (silentFrames >= TRAILING) finish(item, "silence")
        }

        while (!failed && (!closing || frames.isNotEmpty())) {
            val frame = frames.poll(100, TimeUnit.MILLISECONDS) ?: continue
            lastFrame = frame
            pending.addLast(Gated(frame, classify(frame.samples)))
            while (pending.size > LOOKAHEAD && !failed) feed(pending.removeFirst())
        }
        if (!failed) {
            while (pending.isNotEmpty()) feed(pending.removeFirst())
            if (active) {
                val tail = denoiser.flush().samples
                denoisedSamples += tail.size
                lastFrame?.let { consume(tail, it) }
            }
            LogUtil.i(TAG, "drain session_id=$sessionId denoise_calls=$denoiseCalls segments=$segments")
        }
    }

    private fun median(values: FloatArray, count: Int): Float {
        val copy = values.copyOf(count)
        copy.sort()
        return copy[count / 2]
    }

    private fun elapsedMs(since: Long): Long = (System.nanoTime() - since) / 1_000_000

    private companion object {
        const val TAG = "VoiceExperiment"
        const val RATE = VoiceEnrollment.SAMPLE_RATE
        const val WINDOW = 2 * RATE
        const val STEP = RATE / 2
        const val SUBFRAME = RATE / 50
        const val HISTORY = 125
        const val LOOKAHEAD = 3
        const val TRAILING = 3
        const val GAIN = 8f
        const val QUEUE_HIGH = 35
        const val MIN_SPEECH = 6
        const val MAX_SEGMENT = 3 * RATE
        const val SILENCE = 1e-9
        const val FLOOR = 0.0031623f
        const val TARGET = "跳过"
        val PUNCTUATION = Regex("[\\p{P}\\s]")
    }
}
