package com.digitalian.cloudwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.digitalian.cloudwallpaper.databinding.ActivitySettingsBinding
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: WallpaperPrefs
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // クラウド対応: 画像を複数選択（Google Drive/OneDrive/Dropbox対応）
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImagesSelected(uris)
        }
    }

    // ローカルフォルダ選択（従来機能、ローカルのみ）
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onFolderSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = WallpaperPrefs(this)
        setupUI()
        loadCurrentSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun setupUI() {
        // クラウドから画像を選択（メイン機能）
        binding.btnSelectImages.setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        // ローカルフォルダ選択（サブ機能）
        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(prefs.folderUri)
        }

        // 選択クリア
        binding.btnClearImages.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("画像をクリア")
                .setMessage("選択した画像をすべて削除しますか？")
                .setPositiveButton("クリア") { _, _ ->
                    prefs.clearImageUris()
                    prefs.folderUri = null
                    prefs.folderName = ""
                    prefs.currentIndex = 0
                    updateImageDisplay()
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        // 切替間隔
        val intervals = listOf(
            WallpaperPrefs.INTERVAL_1MIN to getString(R.string.interval_1min),
            WallpaperPrefs.INTERVAL_5MIN to getString(R.string.interval_5min),
            WallpaperPrefs.INTERVAL_15MIN to getString(R.string.interval_15min),
            WallpaperPrefs.INTERVAL_30MIN to getString(R.string.interval_30min),
            WallpaperPrefs.INTERVAL_1HOUR to getString(R.string.interval_1hour),
            WallpaperPrefs.INTERVAL_3HOUR to getString(R.string.interval_3hour),
            WallpaperPrefs.INTERVAL_6HOUR to getString(R.string.interval_6hour),
        )
        val intervalAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervals.map { it.second }
        )
        binding.spinnerInterval.adapter = intervalAdapter
        binding.spinnerInterval.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.intervalMs = intervals[position].first
            }
        }

        // トランジション
        val transitions = listOf(
            WallpaperPrefs.TRANSITION_FADE to getString(R.string.transition_fade),
            WallpaperPrefs.TRANSITION_SLIDE to getString(R.string.transition_slide),
            WallpaperPrefs.TRANSITION_NONE to getString(R.string.transition_none),
        )
        val transAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            transitions.map { it.second }
        )
        binding.spinnerTransition.adapter = transAdapter
        binding.spinnerTransition.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.transition = transitions[position].first
            }
        }

        // 表示順序
        val orders = listOf(
            WallpaperPrefs.ORDER_RANDOM to getString(R.string.order_random),
            WallpaperPrefs.ORDER_SEQUENTIAL to getString(R.string.order_sequential),
            WallpaperPrefs.ORDER_DATE_NEWEST to getString(R.string.order_date_newest),
        )
        val orderAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            orders.map { it.second }
        )
        binding.spinnerOrder.adapter = orderAdapter
        binding.spinnerOrder.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.order = orders[position].first
                if (orders[position].first == WallpaperPrefs.ORDER_RANDOM) {
                    prefs.shuffleSeed = System.currentTimeMillis()
                }
            }
        }

        // スケーリング
        val scales = listOf(
            WallpaperPrefs.SCALE_FILL to getString(R.string.scale_fill),
            WallpaperPrefs.SCALE_FIT to getString(R.string.scale_fit),
        )
        val scaleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scales.map { it.second }
        )
        binding.spinnerScale.adapter = scaleAdapter
        binding.spinnerScale.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.scaleMode = scales[position].first
            }
        }

        // 動画設定
        binding.switchVideo.setOnCheckedChangeListener { _, checked ->
            prefs.includeVideos = checked
            binding.layoutVideoOptions.visibility = if (checked) LinearLayout.VISIBLE else LinearLayout.GONE
        }

        binding.switchMuteVideo.setOnCheckedChangeListener { _, checked ->
            prefs.muteVideos = checked
        }

        // 壁紙に設定ボタン
        binding.btnSetWallpaper.setOnClickListener {
            setAsWallpaper()
        }

        // プレビューボタン
        binding.btnPreview.setOnClickListener {
            previewWallpaper()
        }
    }

    private fun loadCurrentSettings() {
        updateImageDisplay()

        val intervalIndex = when (prefs.intervalMs) {
            WallpaperPrefs.INTERVAL_1MIN -> 0
            WallpaperPrefs.INTERVAL_5MIN -> 1
            WallpaperPrefs.INTERVAL_15MIN -> 2
            WallpaperPrefs.INTERVAL_30MIN -> 3
            WallpaperPrefs.INTERVAL_1HOUR -> 4
            WallpaperPrefs.INTERVAL_3HOUR -> 5
            WallpaperPrefs.INTERVAL_6HOUR -> 6
            else -> 2
        }
        binding.spinnerInterval.setSelection(intervalIndex)

        val transIndex = when (prefs.transition) {
            WallpaperPrefs.TRANSITION_FADE -> 0
            WallpaperPrefs.TRANSITION_SLIDE -> 1
            WallpaperPrefs.TRANSITION_NONE -> 2
            else -> 0
        }
        binding.spinnerTransition.setSelection(transIndex)

        val orderIndex = when (prefs.order) {
            WallpaperPrefs.ORDER_RANDOM -> 0
            WallpaperPrefs.ORDER_SEQUENTIAL -> 1
            WallpaperPrefs.ORDER_DATE_NEWEST -> 2
            else -> 0
        }
        binding.spinnerOrder.setSelection(orderIndex)

        val scaleIndex = when (prefs.scaleMode) {
            WallpaperPrefs.SCALE_FILL -> 0
            WallpaperPrefs.SCALE_FIT -> 1
            else -> 0
        }
        binding.spinnerScale.setSelection(scaleIndex)

        binding.switchVideo.isChecked = prefs.includeVideos
        binding.switchMuteVideo.isChecked = prefs.muteVideos
        binding.layoutVideoOptions.visibility =
            if (prefs.includeVideos) LinearLayout.VISIBLE else LinearLayout.GONE
    }

    /** クラウドから画像を選択した時 */
    private fun onImagesSelected(uris: List<Uri>) {
        // 永続的なアクセス権限を取得
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // 一部のプロバイダーは永続権限非対応
            }
        }

        prefs.addImageUris(uris)
        updateImageDisplay()

        Toast.makeText(
            this,
            "${uris.size}枚の画像を追加しました（合計: ${prefs.imageUris.size}枚）",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** ローカルフォルダを選択した時 */
    private fun onFolderSelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        prefs.folderUri = uri
        prefs.folderName = uri.lastPathSegment ?: "Unknown"
        prefs.currentIndex = 0

        updateImageDisplay()
    }

    private fun updateImageDisplay() {
        val cloudCount = prefs.imageUris.size
        val folderUri = prefs.folderUri

        if (cloudCount > 0 || folderUri != null) {
            val parts = mutableListOf<String>()
            if (cloudCount > 0) {
                parts.add("クラウド/選択: ${cloudCount}枚")
            }
            if (folderUri != null) {
                parts.add("フォルダ: ${prefs.folderName}")
            }
            binding.tvImageCount.text = parts.joinToString(" + ")
            binding.tvImageCount.visibility = TextView.VISIBLE
            binding.btnClearImages.visibility = android.view.View.VISIBLE

            // フォルダがある場合は中身もカウント
            if (folderUri != null) {
                scope.launch {
                    val folderCount = withContext(Dispatchers.IO) {
                        ImageSource(this@SettingsActivity)
                            .listMedia(folderUri, prefs.includeVideos).size
                    }
                    val total = cloudCount + folderCount
                    binding.tvImageCount.text = getString(R.string.image_count, total)
                }
            } else {
                binding.tvImageCount.text = getString(R.string.image_count, cloudCount)
            }
        } else {
            binding.tvImageCount.text = getString(R.string.no_folder_selected)
            binding.tvImageCount.visibility = TextView.VISIBLE
            binding.btnClearImages.visibility = android.view.View.GONE
        }
    }

    private fun setAsWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@SettingsActivity, CloudWallpaperService::class.java)
            )
        }
        startActivity(intent)
    }

    private fun previewWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        startActivity(intent)
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onSelected(position: Int)
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
