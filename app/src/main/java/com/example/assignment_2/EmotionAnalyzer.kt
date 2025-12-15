package com.example.assignment_2
//package com.example.videomoderation

import kotlin.math.abs

class EmotionAnalyzer {

    private var lastDb: Double? = null

    /**
     * db: dBFS（通常为负数，越接近0越响）
     * timeMs: 当前音频时间戳
     */
    fun update(db: Double, timeMs: Long): String {
        val prev = lastDb
        lastDb = db

        // 突增判定：可能“震惊/激动”
        val jump = if (prev != null) db - prev else 0.0

        return when {
            db > -10 && jump > 6 -> "可能震惊/激动（音量突增 ${jump.toInt()} dB, 当前 ${db.toInt()} dBFS）"
            db > -12 -> "激动/高兴（响度高 ${db.toInt()} dBFS）"
            db < -32 -> "低落/平静（响度低 ${db.toInt()} dBFS）"
            abs(jump) > 8 -> "情绪波动（响度变化 ${jump.toInt()} dB, 当前 ${db.toInt()} dBFS）"
            else -> "正常（${db.toInt()} dBFS）"
        }
    }
}

