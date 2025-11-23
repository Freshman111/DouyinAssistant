package com.sgffsg.douyin.assist.recorder

import androidx.core.math.MathUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

/**
 * Created by xuyang on 2021/1/20.
 */
class AudioChunk(
    val audioType: AudioType,
    val audioBytes: ByteArray
) {

    private var audioShorts: ShortArray? = null

    fun toShorts(): ShortArray {
        if (audioShorts != null) {
            return audioShorts!!
        }
        audioShorts = ShortArray(audioBytes.size / 2)
        //Byte转Short，注意nativeOrder大小端
        ByteBuffer.wrap(audioBytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(audioShorts)
        return audioShorts!!
    }

    /**
     * Returns a numeric representation of the audio energy of the current audio chunk.
     * The audio level is within the range from 0 to 90, the value is proportional to
     * the loudness of the audio. Based on the experiments, only the audio with the
     * energy value greater than 40 will be heard by normal human ears.
     *
     * @return audio volume (0 - 1)
     */
    fun getVolume(): Float {
        val acc: Long = accumulate(toShorts())
        return MathUtils.clamp((90 + computeAverageLevel(acc)) / 90, 0.0f, 1.0f)
    }

    /**
     * A group of helper functions for generating the audio energy
     * level values.
     *
     * @param shorts
     * @return audio energy level
     */
    private fun accumulate(shorts: ShortArray): Long {
        var acc: Long = 0
        var `val`: Long
        for (i in shorts.indices) {
            `val` = shorts[i].toLong()
            acc += `val` * `val` shr 9
        }
        return acc
    }

    private fun computeAverageLevel(acc: Long): Float {
        var dbpwr: Double
        val pwr = acc / (32768.0 * 32768.0)
        if (pwr < 1e-9)
            dbpwr = -90.0
        else {
            dbpwr = 10 * log10(pwr)
            if (dbpwr > 0) dbpwr = 0.0
        }
        return dbpwr.toFloat()
    }

}