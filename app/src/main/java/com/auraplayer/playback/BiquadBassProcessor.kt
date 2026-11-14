package com.auraplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

/** A tiny 16-bit PCM low-shelf DSP stage; no continuous platform effect or extra audio thread. */
class BiquadBassProcessor(
    private val frequencyHz: Double = 105.0,
    private val gainDb: Double = 5.5,
    private val shelfSlope: Double = 1.0
) : AudioProcessor {
    private var format = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var states = emptyArray<State>()
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private data class State(var x1: Double = 0.0, var x2: Double = 0.0, var y1: Double = 0.0, var y2: Double = 0.0)

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        format = inputAudioFormat
        states = Array(inputAudioFormat.channelCount) { State() }
        calculateCoefficients(inputAudioFormat.sampleRate.toDouble())
        return inputAudioFormat
    }

    override fun isActive(): Boolean = format != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        val processed = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        var frameChannel = 0
        while (inputBuffer.remaining() >= 2) {
            val x = inputBuffer.short.toDouble() / 32768.0
            val s = states[frameChannel]
            val y = (b0 * x + b1 * s.x1 + b2 * s.x2 - a1 * s.y1 - a2 * s.y2)
                .coerceIn(-1.0, 0.999969)
            s.x2 = s.x1; s.x1 = x; s.y2 = s.y1; s.y1 = y
            processed.putShort((y * 32768.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            frameChannel = (frameChannel + 1) % states.size
        }
        inputBuffer.position(inputBuffer.limit())
        processed.flip()
        outputBuffer = processed
    }

    override fun getOutput(): ByteBuffer {
        val result = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return result
    }

    override fun queueEndOfStream() = Unit
    override fun isEnded(): Boolean = outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        states.forEach { it.x1 = 0.0; it.x2 = 0.0; it.y1 = 0.0; it.y2 = 0.0 }
    }

    override fun reset() {
        flush()
        format = AudioProcessor.AudioFormat.NOT_SET
        states = emptyArray()
    }

    private fun calculateCoefficients(sampleRate: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * frequencyHz / sampleRate
        val cosW = kotlin.math.cos(w0)
        val sinW = kotlin.math.sin(w0)
        val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / shelfSlope - 1.0) + 2.0)
        val beta = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW + beta
        b0 = a * ((a + 1.0) - (a - 1.0) * cosW + beta) / a0
        b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW) / a0
        b2 = a * ((a + 1.0) - (a - 1.0) * cosW - beta) / a0
        a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosW - beta) / a0
    }
}
