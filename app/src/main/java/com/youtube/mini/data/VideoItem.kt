package com.youtube.mini.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val dateAddedSec: Long,
)
