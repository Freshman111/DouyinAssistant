package com.sgffsg.douyin.assist.kws

import com.sgffsg.douyin.assist.common.utils.LogUtil

internal class KwsDiagnostics(private val sessionId: Int, sampleRate: Int, audioSource: Int) {
    private val startedAt = System.nanoTime()
    private var windowStartedAt = startedAt
    private var inputSamples = 0L
    private var pcmPosition: PcmPosition? = null
    private var blocks = 0
    private var zeroBlocks = 0
    private var readErrors = 0
    private var lastReadError = 0
    private var maxReadNanos = 0L
    private var maxWriteNanos = 0L
    private var maxKwsNanos = 0L
    private var nextHitId = 0L

    init {
        event("start", "sample_rate=$sampleRate source=$audioSource")
    }

    fun recordRead(samples: ShortArray, count: Int, pcm: PcmPosition?, readNanos: Long, writeNanos: Long) {
        if (count > 0) {
            inputSamples += count
            pcmPosition = pcm
            blocks++
            if ((0 until count).all { samples[it] == 0.toShort() }) zeroBlocks++
        } else if (count < 0) {
            readErrors++
            lastReadError = count
        }
        maxReadNanos = maxOf(maxReadNanos, readNanos)
        maxWriteNanos = maxOf(maxWriteNanos, writeNanos)
    }

    fun recordProcessing(kwsNanos: Long) {
        maxKwsNanos = maxOf(maxKwsNanos, kwsNanos)
        val now = System.nanoTime()
        if (now - windowStartedAt >= 1_000_000_000L) writeWindow(now)
    }

    fun hit(keyword: String, command: Command?): (String) -> Unit {
        val hitId = ++nextHitId
        val hitPosition = position()
        event("kws_hit", "id=$hitId keyword=$keyword command=${command?.name ?: "unmatched"}")
        return { result -> LogUtil.d(TAG, "swipe $hitPosition id=$hitId result=$result") }
    }

    private fun event(name: String, details: String) {
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        LogUtil.d(TAG, "$name ${position()} elapsed_ms=$elapsedMs $details")
    }

    private fun position(): String {
        val trace = "session_id=$sessionId trace_id=$startedAt input_samples=$inputSamples"
        val pcm = pcmPosition ?: return trace
        return "$trace pcm_file=${pcm.fileName} pcm_end_sample=${pcm.samplesWritten}"
    }

    fun finish() {
        if (blocks > 0 || readErrors > 0) writeWindow(System.nanoTime())
        event("stop", "")
    }

    private fun writeWindow(now: Long) {
        event(
            "audio_window",
            "blocks=$blocks zero_blocks=$zeroBlocks " +
                "read_errors=$readErrors last_read_error=$lastReadError " +
                "max_read_us=${maxReadNanos / 1000} max_write_us=${maxWriteNanos / 1000} " +
                "max_kws_and_dispatch_us=${maxKwsNanos / 1000}"
        )
        windowStartedAt = now
        blocks = 0
        zeroBlocks = 0
        readErrors = 0
        lastReadError = 0
        maxReadNanos = 0
        maxWriteNanos = 0
        maxKwsNanos = 0
    }

    private companion object {
        const val TAG = "KwsDiagnostics"
    }
}
