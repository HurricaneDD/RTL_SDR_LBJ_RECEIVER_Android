package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class FftProcessor(
    val fftSize: Int = 1024,
    val numBands: Int = 32
) {
    private val hanningWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val winMean: Float
    private val realBuf = FloatArray(fftSize)
    private val imagBuf = FloatArray(fftSize)
    private val fullDbBuf = FloatArray(fftSize)
    private val pooledBuf = FloatArray(numBands)
    private val barsOutput = FloatArray(numBands)

    init {
        var sum = 0.0
        for (w in hanningWindow) sum += w
        winMean = (sum / fftSize).toFloat()
    }

    val smoothedBars = FloatArray(numBands) { -120.0f }

    data class PeakInfo(
        val peakFreqHz: Double?,
        val peakDeltaHz: Double?,
        val peakDb: Float?
    )

    data class FftResult(
        val bandsDb: FloatArray,
        val fullSpectrumDb: FloatArray,
        val peakInfo: PeakInfo
    )

    /**
     * Compute FFT on the first `fftSize` complex samples.
     */
    fun process(
        iqBuffer: ComplexBuffer,
        sampleRate: Double,
        hwFreqHz: Double,
        targetFreqHz: Double,
        bwKhz: Double
    ): FftResult {
        val n = fftSize
        if (iqBuffer.size < n) {
            return FftResult(smoothedBars.clone(), FloatArray(n) { -120f }, PeakInfo(null, null, null))
        }

        // Apply Hanning window
        val real = realBuf
        val imag = imagBuf
        for (i in 0 until n) {
            val w = hanningWindow[i]
            real[i] = iqBuffer.real[i] * w
            imag[i] = iqBuffer.imag[i] * w
        }

        // Perform in-place Cooley-Tukey Radix-2 FFT
        fftRadix2(real, imag, n)

        // FFT Shift (swap halves) and compute magnitude in dB
        val fullDb = fullDbBuf
        val half = n / 2
        val scale = n * winMean

        for (i in 0 until n) {
            val srcIdx = (i + half) % n
            val r = real[srcIdx]
            val im = imag[srcIdx]
            val mag = sqrt(r * r + im * im) / scale
            val db = (20.0 * log10(max(1e-12, mag.toDouble()))).toFloat()
            fullDb[i] = db
        }

        // Pool into numBands
        val chunkSize = n / numBands
        for (b in 0 until numBands) {
            var maxV = -999.0f
            val start = b * chunkSize
            val end = (b + 1) * chunkSize
            for (k in start until end) {
                if (fullDb[k] > maxV) maxV = fullDb[k]
            }
            // Exponential smoothing
            smoothedBars[b] = 0.7f * smoothedBars[b] + 0.3f * maxV
            barsOutput[b] = smoothedBars[b]
        }

        // Peak detector within target channel bandwidth
        val targetOffsetHz = targetFreqHz - hwFreqHz
        val targetBwHz = max(1000.0, bwKhz * 1000.0)
        val minOffset = targetOffsetHz - targetBwHz * 0.5
        val maxOffset = targetOffsetHz + targetBwHz * 0.5

        var peakIdx = -1
        var peakVal = -999.0f
        for (i in 0 until n) {
            // frequency for shifted index i
            // index 0 -> -sampleRate/2, index half -> 0, index n-1 -> sampleRate/2
            val freqOffset = ((i - half).toDouble() / n) * sampleRate
            if (freqOffset in minOffset..maxOffset) {
                if (fullDb[i] > peakVal) {
                    peakVal = fullDb[i]
                    peakIdx = i
                }
            }
        }

        val peakInfo = if (peakIdx >= 0) {
            val peakFreqOffset = ((peakIdx - half).toDouble() / n) * sampleRate
            val peakFreq = hwFreqHz + peakFreqOffset
            PeakInfo(
                peakFreqHz = peakFreq,
                peakDeltaHz = peakFreq - targetFreqHz,
                peakDb = peakVal
            )
        } else {
            PeakInfo(null, null, null)
        }

        return FftResult(smoothedBars.clone(), fullDb, peakInfo)
    }

    private fun fftRadix2(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                val tempI = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempR
                imag[j] = tempI
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len *= 2
        }
    }
}
