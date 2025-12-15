package com.example.assignment_2
//package com.example.videomoderation

import android.content.Context

class ProfanityDetector(private val patterns: List<String>) {

    fun count(text: String): Map<String, Int> {
        if (text.isBlank()) return emptyMap()
        val lower = text.lowercase()

        val out = linkedMapOf<String, Int>()
        for (p in patterns) {
            if (p.isBlank()) continue
            val key = p
            // 简单包含计数（你也可以换成更严格的分词/正则边界）
            var idx = 0
            var c = 0
            while (true) {
                val found = lower.indexOf(p.lowercase(), idx)
                if (found < 0) break
                c++
                idx = found + p.length
            }
            if (c > 0) out[key] = c
        }
        return out
    }

    companion object {
        fun fromAssets(context: Context, assetPath: String): ProfanityDetector {
            val lines = runCatching {
                context.assets.open(assetPath).bufferedReader().useLines { seq ->
                    seq.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                }
            }.getOrDefault(emptyList())

            return ProfanityDetector(lines)
        }
    }
}

