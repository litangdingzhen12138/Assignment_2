package com.example.assignment_2
//package com.example.videomoderation

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ModerationUi(
    val status: String,
    val transcript: String,
    val totalProfanityCount: Int,
    val detailCounts: String,
    val emotionLabel: String
)

class VideoAudioModerationEngine(
    private val context: Context,
    private val lang: Lang,
    private val profanityDetector: ProfanityDetector,
    private val emotionAnalyzer: EmotionAnalyzer,
    private val onUi: (ModerationUi) -> Unit
) {

    suspend fun run(source: VideoSource) = withContext(Dispatchers.Default) {
        onUi(ModerationUi("加载模型…", "", 0, "", "-"))

        // 1) 准备 Vosk 模型（assets → filesDir）
        val modelDir = when (lang) {
            Lang.ZH -> "vosk_models/zh"
            Lang.EN -> "vosk_models/en"
        }
        val modelPath = AssetCopier.copyAssetDirToFilesIfNeeded(context, modelDir)

        val model = Model(modelPath)
        val recognizer = Recognizer(model, 16000.0f).apply {
            setWords(true)
        }

        // 2) 解码视频音轨 → PCM → (重采样到16k + 单声道) → Vosk
        val state = ModerationState()

//        onUi(state.toUi("解码音轨中…"))
        onUi(state.toUi("解码音轨中…", profanityDetector))
        decodeAudioPcm(source) { pcmBytes, sampleRate, channelCount, ptsUs ->
            // (A) dBFS 情绪分析（用解码后的原始采样率也可以）
            val mono16 = pcmBytesToMonoShorts(pcmBytes, channelCount)
            val db = computeDbfs(mono16)
            val emotion = emotionAnalyzer.update(db, ptsUs / 1000)

            // (B) 重采样到 16k（Vosk 推荐 16k/16bit/mono）
            val mono16k = resampleTo16k(mono16, sampleRate)
            val mono16kBytes = shortsToLeBytes(mono16k)

            val isFinal = recognizer.acceptWaveForm(mono16kBytes, mono16kBytes.size)
            if (isFinal) {
                val text = parseVoskText(recognizer.result)
                if (text.isNotBlank()) {
                    state.appendFinal(text)
                    state.addProfanity(profanityDetector.count(text))
                }
            } else {
                val partial = parseVoskPartial(recognizer.partialResult)
                state.setPartial(partial)
            }

            state.emotionLabel = emotion

//            onUi(state.toUi("识别中…"))
            onUi(state.toUi("识别中…", profanityDetector))
            // 继续
        }

        recognizer.close()
        model.close()

//        onUi(state.toUi("完成"))
        onUi(state.toUi("完成", profanityDetector))
    }

    private fun decodeAudioPcm(
        source: VideoSource,
        onPcm: (pcm: ByteArray, sampleRate: Int, channelCount: Int, ptsUs: Long) -> Unit
    ) {
        val extractor = MediaExtractor()

        when (source) {
            is VideoSource.FromUri -> {
                extractor.setDataSource(context, source.uri, null)
            }
            is VideoSource.FromAsset -> {
                val afd = context.assets.openFd(source.assetPath)
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
        }

        val audioTrack = (0 until extractor.trackCount).firstOrNull { i ->
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return@firstOrNull false
            mime.startsWith("audio/")
        } ?: run {
            throw IllegalStateException("找不到音轨（audio track）")
        }

        extractor.selectTrack(audioTrack)
        val inputFormat = extractor.getTrackFormat(audioTrack)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            // in
            if (!sawInputEOS) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEOS = true
                    } else {
                        val ptsUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0)
                        extractor.advance()
                    }
                }
            }

            // out
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    if (bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val pcm = ByteArray(bufferInfo.size)
                        outBuf.get(pcm)

                        onPcm(pcm, sampleRate, channelCount, bufferInfo.presentationTimeUs)
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outFmt = codec.outputFormat
                    // 某些机型会在这里给出更准确的采样率/声道数
                    sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    Log.d("Moderation", "Output format changed: $sampleRate Hz, ch=$channelCount")
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
    }

    private fun parseVoskText(json: String): String =
        runCatching { JSONObject(json).optString("text", "") }.getOrDefault("")

    private fun parseVoskPartial(json: String): String =
        runCatching { JSONObject(json).optString("partial", "") }.getOrDefault("")

    private fun pcmBytesToMonoShorts(pcmLe: ByteArray, channelCount: Int): ShortArray {
        // 默认按 PCM 16-bit little endian 解读
        val shortCount = pcmLe.size / 2
        val samples = ShortArray(shortCount)
        ByteBuffer.wrap(pcmLe).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        if (channelCount <= 1) return samples

        // 下混：多声道 → 单声道（简单平均）
        val frames = shortCount / channelCount
        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += samples[i * channelCount + ch].toInt()
            }
            mono[i] = (sum / channelCount).toShort()
        }
        return mono
    }

    private fun computeDbfs(mono: ShortArray): Double {
        if (mono.isEmpty()) return -120.0
        var acc = 0.0
        for (s in mono) {
            val v = s.toDouble() / 32768.0
            acc += v * v
        }
        val rms = sqrt(acc / mono.size)
        val db = 20.0 * log10(max(1e-9, rms))
        return db
    }

    private fun resampleTo16k(src: ShortArray, srcRate: Int): ShortArray {
        if (srcRate == 16000) return src
        if (src.isEmpty()) return src
        val ratio = 16000.0 / srcRate.toDouble()
        val dstLen = max(1, (src.size * ratio).roundToInt())
        val dst = ShortArray(dstLen)
        for (i in 0 until dstLen) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt()
            val i1 = min(i0 + 1, src.size - 1)
            val frac = srcPos - i0
            val v = (src[i0] * (1.0 - frac) + src[i1] * frac)
            dst[i] = v.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return dst
    }

    private fun shortsToLeBytes(src: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(src.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in src) bb.putShort(s)
        return bb.array()
    }
}

private class ModerationState {
    private val finals = StringBuilder()
    private var partial: String = ""
    private val counts = linkedMapOf<String, Int>()
    var emotionLabel: String = "-"

    fun appendFinal(text: String) {
        finals.append(text).append('\n')
        partial = ""
    }

    fun setPartial(text: String) {
        partial = text
    }

    fun addProfanity(found: Map<String, Int>) {
        for ((k, v) in found) {
            counts[k] = (counts[k] ?: 0) + v
        }
    }
    fun toUi(status: String, profanityDetector: ProfanityDetector): ModerationUi {
        // final累计（已确认）
        val combined = linkedMapOf<String, Int>()
        for ((k, v) in counts) combined[k] = v

        // partial临时统计（只用于显示，不写入 counts）
        if (partial.isNotBlank()) {
            val temp = profanityDetector.count(partial)
            for ((k, v) in temp) combined[k] = (combined[k] ?: 0) + v
        }

        val total = combined.values.sum()
        val detail = combined.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (k, v) -> "$k : $v" }

        val transcript = buildString {
            append(finals)
            if (partial.isNotBlank()) append("…").append(partial)
        }

        return ModerationUi(
            status = status,
            transcript = transcript,
            totalProfanityCount = total,
            detailCounts = detail,
            emotionLabel = emotionLabel
        )
    }

//    fun toUi(status: String): ModerationUi {
//        val total = counts.values.sum()
//        val detail = counts.entries
//            .sortedByDescending { it.value }
//            .joinToString("\n") { (k, v) -> "$k : $v" }
//
//        val transcript = buildString {
//            append(finals)
//            if (partial.isNotBlank()) {
//                append("…").append(partial)
//            }
//        }
//
//        return ModerationUi(
//            status = status,
//            transcript = transcript,
//            totalProfanityCount = total,
//
//            detailCounts = detail,
//            emotionLabel = emotionLabel
//        )
//    }
}

