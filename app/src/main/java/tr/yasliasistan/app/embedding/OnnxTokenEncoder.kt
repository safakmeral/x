package tr.yasliasistan.app.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import tr.yasliasistan.core.embedding.E5TextEmbedder
import tr.yasliasistan.core.embedding.TokenEncoder
import java.io.File
import java.nio.LongBuffer

/**
 * Runs the multilingual-e5-small ONNX graph with ONNX Runtime Android (MIT) on the CPU. Inputs:
 * input_ids, attention_mask (all ones: one unpadded sequence), token_type_ids (all zeros); output:
 * last_hidden_state. Everything stays in memory on the device.
 */
class OnnxTokenEncoder(modelFile: File, threads: Int = DEFAULT_THREADS) : TokenEncoder {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(threads)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            session = env.createSession(modelFile.absolutePath, options)
        }
    }

    override val dimension: Int = E5TextEmbedder.DIMENSION

    override fun lastHiddenState(ids: LongArray): FloatArray {
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { inputIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape).use { mask ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape).use { types ->
                    val inputs = mapOf("input_ids" to inputIds, "attention_mask" to mask, "token_type_ids" to types)
                    session.run(inputs).use { result ->
                        val buffer = (result.get(0) as OnnxTensor).floatBuffer
                        return FloatArray(buffer.remaining()).also { buffer.get(it) }
                    }
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        /** Two threads: the A15's two fast cores; G-43 device run compares 1/2/4. */
        const val DEFAULT_THREADS = 2
    }
}
