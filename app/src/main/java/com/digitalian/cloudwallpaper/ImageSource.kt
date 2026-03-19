package com.digitalian.cloudwallpaper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * SAF (Storage Access Framework) を使って、クラウドストレージ含む
 * 任意のフォルダから画像・動画ファイルを列挙する
 */
class ImageSource(private val context: Context) {

    data class MediaItem(
        val uri: Uri,
        val name: String,
        val isVideo: Boolean,
        val lastModified: Long
    )

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov")

    /**
     * 指定フォルダURIから画像・動画を再帰的に取得
     */
    fun listMedia(folderUri: Uri, includeVideos: Boolean = true): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return items
        collectMedia(docFile, items, includeVideos)
        return items
    }

    private fun collectMedia(
        folder: DocumentFile,
        items: MutableList<MediaItem>,
        includeVideos: Boolean
    ) {
        for (file in folder.listFiles()) {
            if (file.isDirectory) {
                collectMedia(file, items, includeVideos)
                continue
            }

            val name = file.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            val mimeType = file.type ?: ""

            val isImage = ext in imageExtensions || mimeType.startsWith("image/")
            val isVideo = ext in videoExtensions || mimeType.startsWith("video/")

            if (isImage || (includeVideos && isVideo)) {
                items.add(
                    MediaItem(
                        uri = file.uri,
                        name = name,
                        isVideo = isVideo,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    /**
     * 高速版: ContentResolver query で直接列挙（SAF Tree Document）
     */
    fun listMediaFast(treeUri: Uri, includeVideos: Boolean = true): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docIdChild = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: continue
                val lastMod = cursor.getLong(modIdx)

                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")

                if (isImage || (includeVideos && isVideo)) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docIdChild)
                    items.add(MediaItem(fileUri, name, isVideo, lastMod))
                }
            }
        }

        return items
    }
}
