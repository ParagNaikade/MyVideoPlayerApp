package com.youtube.mini.data

import android.content.Context
import android.provider.MediaStore

class LocalVideoRepository(private val context: Context) {

    fun getVideos(): List<VideoItem> {
    fun getVideos(sourceFolders: Set<String> = emptySet()): List<VideoItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val videos = mutableListOf<VideoItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx) ?: "Untitled"
                val durationMs = cursor.getLong(durationIdx)
                val dateAddedSec = cursor.getLong(dateIdx)
                val filePath = cursor.getString(pathIdx) ?: ""
                
                if (sourceFolders.isNotEmpty()) {
                    val parentDir = java.io.File(filePath).parentFile?.absolutePath ?: ""
                    if (!sourceFolders.any { selectedFolder -> parentDir.startsWith(selectedFolder) }) {
                        continue
                    }
                }
                
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString())
                    .build()

                videos += VideoItem(
                    id = id,
                    uri = contentUri,
                    title = title,
                    durationMs = durationMs,
                    dateAddedSec = dateAddedSec,
                )
            }
        }

        return videos
    }
}
