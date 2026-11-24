package rs.snoreguard.mvp

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/** TFLite YAMNet, 16 kHz mono, 15600 samples, label index 38 = Snoring. */
class YamnetSnoreDetector(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(SAMPLES * 4).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(521) }

    init {
        val afd = context.assets.openFd("yamnet.tflite")
        val mapped = afd.use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }
        interpreter = Interpreter(mapped, Interpreter.Options().setNumThreads(2))
        require(interpreter.getInputTensor(0).numElements() == SAMPLES) {
            "Unexpected YAMNet input shape"
        }
        require(interpreter.getOutputTensor(0).numElements() == 521) {
            "Unexpected YAMNet output shape"
        }
    }

    /** Returns YAMNet's Snoring CLASS SCORE; not a medical confidence percentage. */
    fun classify(frame: ShortArray): Float {
        require(frame.size == SAMPLES)
        input.rewind()
        var sumSq = 0.0
        for (pcm in frame) {
            val sample = pcm.toFloat() / 32768f
            input.putFloat(sample)
            sumSq += sample.toDouble() * sample.toDouble()
        }
        if (sqrt(sumSq / frame.size) < Settings.MIN_RMS) return 0f
        input.rewind()
        interpreter.run(input, output)
        return output[0][SNORE_INDEX].coerceIn(0f, 1f)
    }

    override fun close() { interpreter.close() }

    companion object {
        const val SAMPLES = 15_600
        const val SNORE_INDEX = 38
    }
}
