package com.example.silero.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD (v5) ONNX 封装。
 *
 * 关键：v5 模型每次推理的输入不是 512 个新采样，而是
 *   「上一帧末尾的 64 个采样(context) + 本帧 512 个新采样 = 576」。
 * 缺了这 64 个 context，模型概率会恒定接近 0（永远判静音）。
 *
 * 模型输入：
 *   - input: float[1][576]   （64 context + 512 新采样）
 *   - state: float[2][1][128]
 *   - sr:    int64 标量
 * 模型输出：
 *   - output: float[1][1] 语音概率
 *   - stateN: float[2][1][128]
 */
class SileroVad(
    context: Context,
    private val sampleRate: Int = 16000,
) {
    companion object {
        const val WINDOW_SIZE = 512   // 每帧新采样数（32ms @16kHz）
        const val CONTEXT_SIZE = 64   // 拼接到帧前的上文采样数
        private const val INPUT_SIZE = CONTEXT_SIZE + WINDOW_SIZE  // 576
        private const val MODEL_ASSET = "silero_vad.onnx"
        private const val TAG = "SileroVad"
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private var state: Array<Array<FloatArray>> = newState()
    private var contextBuf = FloatArray(CONTEXT_SIZE)   // 上一帧末尾 64 采样
    private val inputBuf = FloatArray(INPUT_SIZE)

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
        Log.i(TAG, "VAD 模型加载完成，输入窗口=$INPUT_SIZE (ctx=$CONTEXT_SIZE + win=$WINDOW_SIZE)")
    }

    private fun newState() = Array(2) { Array(1) { FloatArray(128) } }

    /** 重置内部状态与 context（每段录音开始前调用）。 */
    fun reset() {
        state = newState()
        contextBuf = FloatArray(CONTEXT_SIZE)
    }

    /**
     * 处理一帧（[WINDOW_SIZE] 个 float 采样，范围 [-1,1]），返回语音概率 [0,1]。
     */
    fun process(frame: FloatArray): Float {
        require(frame.size == WINDOW_SIZE) { "frame 必须为 $WINDOW_SIZE 采样" }

        // 拼接：[ context(64) | frame(512) ] -> 576
        System.arraycopy(contextBuf, 0, inputBuf, 0, CONTEXT_SIZE)
        System.arraycopy(frame, 0, inputBuf, CONTEXT_SIZE, WINDOW_SIZE)
        // 更新 context = 本帧末尾 64 采样，供下一帧使用
        System.arraycopy(frame, WINDOW_SIZE - CONTEXT_SIZE, contextBuf, 0, CONTEXT_SIZE)

        val inputTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(inputBuf), longArrayOf(1, INPUT_SIZE.toLong())
        )
        val stateFlat = FloatArray(2 * 1 * 128)
        var idx = 0
        for (i in 0 until 2) for (k in 0 until 128) {
            stateFlat[idx++] = state[i][0][k]
        }
        val stateTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(stateFlat), longArrayOf(2, 1, 128)
        )
        val srTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(longArrayOf(sampleRate.toLong())), longArrayOf()
        )

        val inputs = mapOf(
            "input" to inputTensor,
            "state" to stateTensor,
            "sr" to srTensor,
        )

        var prob: Float
        session.run(inputs).use { results ->
            @Suppress("UNCHECKED_CAST")
            val out = results.get(0).value as Array<FloatArray>
            prob = out[0][0]

            @Suppress("UNCHECKED_CAST")
            val newStateArr = results.get(1).value as Array<Array<FloatArray>>
            state = newStateArr
        }

        inputTensor.close()
        stateTensor.close()
        srTensor.close()
        return prob
    }

    fun close() {
        session.close()
    }
}
