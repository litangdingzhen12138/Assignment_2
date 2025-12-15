package com.example.assignment_2

import android.net.Uri

enum class Lang { ZH, EN }

sealed class VideoSource {
    data class FromAsset(val assetPath: String) : VideoSource()
    data class FromUri(val uri: Uri) : VideoSource()
}