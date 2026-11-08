package app.klar.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT with precomputed tables. */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, inverse = false)

    /** Inverse transform, including the 1/N scaling. */
    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, inverse = true)
        val scale = 1f / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val c = cosTable[k]
                    val s = if (inverse) sinTable[k] else -sinTable[k]
                    val l = j + half
                    val tr = re[l] * c - im[l] * s
                    val ti = re[l] * s + im[l] * c
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += len
            }
            len *= 2
        }
    }
}
