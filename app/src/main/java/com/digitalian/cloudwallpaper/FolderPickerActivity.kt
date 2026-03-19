package com.digitalian.cloudwallpaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * フォルダ選択専用のActivityランチャー
 * 他のActivityから呼び出し可能
 */
class FolderPickerActivity : AppCompatActivity() {

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val prefs = WallpaperPrefs(this)
            prefs.folderUri = uri
            prefs.folderName = uri.lastPathSegment ?: "Unknown"
            prefs.currentIndex = 0
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = WallpaperPrefs(this)
        folderPicker.launch(prefs.folderUri)
    }
}
