package com.example.assignment_2
//package com.example.videomoderation

import android.content.Context
import java.io.File

object AssetCopier {

    /**
     * 将 assets 下的某个目录完整复制到 filesDir 下（若已存在则直接复用）
     * 返回复制后的绝对路径（给 Vosk Model 用）
     */
    fun copyAssetDirToFilesIfNeeded(context: Context, assetDir: String): String {
        val outDir = File(context.filesDir, assetDir)
        if (outDir.exists() && outDir.isDirectory && outDir.list()?.isNotEmpty() == true) {
            return outDir.absolutePath
        }
        outDir.mkdirs()
        copyDir(context, assetDir, outDir)
        return outDir.absolutePath
    }

    private fun copyDir(context: Context, assetPath: String, outDir: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: return
        for (name in children) {
            val childAssetPath = "$assetPath/$name"
            val childList = assets.list(childAssetPath)
            if (childList != null && childList.isNotEmpty()) {
                val subDir = File(outDir, name)
                subDir.mkdirs()
                copyDir(context, childAssetPath, subDir)
            } else {
                assets.open(childAssetPath).use { input ->
                    File(outDir, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
