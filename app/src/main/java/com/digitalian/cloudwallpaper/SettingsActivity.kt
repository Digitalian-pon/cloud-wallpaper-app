package com.digitalian.cloudwallpaper

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

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onImagesSelected(uris)
    }

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

    override fun onResume() {
        super.onResume()
        updateWallpaperStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun setupUI() {
        // STEP 1: 画像選択
        binding.btnSelectImages.setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(prefs.folderUri)
        }

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

        // STEP 2: 壁紙スライドショー ON/OFF
        binding.btnToggleWallpaper.setOnClickListener {
            toggleWallpaperSlideshow()
        }

        // 切替間隔
        val intervals = listOf(
            WallpaperPrefs.INTERVAL_15MIN to getString(R.string.interval_15min),
            WallpaperPrefs.INTERVAL_30MIN to getString(R.string.interval_30min),
            WallpaperPrefs.INTERVAL_1HOUR to getString(R.string.interval_1hour),
            WallpaperPrefs.INTERVAL_3HOUR to getString(R.string.interval_3hour),
            WallpaperPrefs.INTERVAL_6HOUR to getString(R.string.interval_6hour),
        )
        binding.spinnerInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            intervals.map { it.second }
        )
        binding.spinnerInterval.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.intervalMs = intervals[position].first
            }
        }

        // 表示順序
        val orders = listOf(
            WallpaperPrefs.ORDER_RANDOM to getString(R.string.order_random),
            WallpaperPrefs.ORDER_SEQUENTIAL to getString(R.string.order_sequential),
        )
        binding.spinnerOrder.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            orders.map { it.second }
        )
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
        binding.spinnerScale.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            scales.map { it.second }
        )
        binding.spinnerScale.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onSelected(position: Int) {
                prefs.scaleMode = scales[position].first
            }
        }

        // その他モード
        binding.btnPhotoFrame.setOnClickListener {
            startActivity(Intent(this, PhotoFrameActivity::class.java))
        }
        binding.btnInternetTv.setOnClickListener {
            startActivity(Intent(this, InternetTvActivity::class.java))
        }
    }

    private fun loadCurrentSettings() {
        updateImageDisplay()

        // 間隔
        val intervalIndex = when (prefs.intervalMs) {
            WallpaperPrefs.INTERVAL_15MIN -> 0
            WallpaperPrefs.INTERVAL_30MIN -> 1
            WallpaperPrefs.INTERVAL_1HOUR -> 2
            WallpaperPrefs.INTERVAL_3HOUR -> 3
            WallpaperPrefs.INTERVAL_6HOUR -> 4
            else -> 0
        }
        binding.spinnerInterval.setSelection(intervalIndex)

        // 順序
        val orderIndex = when (prefs.order) {
            WallpaperPrefs.ORDER_RANDOM -> 0
            WallpaperPrefs.ORDER_SEQUENTIAL -> 1
            else -> 0
        }
        binding.spinnerOrder.setSelection(orderIndex)

        // スケール
        val scaleIndex = when (prefs.scaleMode) {
            WallpaperPrefs.SCALE_FILL -> 0
            WallpaperPrefs.SCALE_FIT -> 1
            else -> 0
        }
        binding.spinnerScale.setSelection(scaleIndex)

        updateWallpaperStatus()
    }

    private fun toggleWallpaperSlideshow() {
        if (WallpaperChangeWorker.isRunning(this)) {
            // 停止
            WallpaperChangeWorker.stop(this)
            Toast.makeText(this, "壁紙スライドショーを停止しました", Toast.LENGTH_SHORT).show()
        } else {
            // 開始前に画像があるか確認
            val count = prefs.imageUris.size
            if (count == 0 && prefs.folderUri == null) {
                Toast.makeText(this, "まず画像を選択してください", Toast.LENGTH_SHORT).show()
                return
            }

            WallpaperChangeWorker.start(this, prefs.intervalMs)
            Toast.makeText(this, "壁紙スライドショーを開始しました！", Toast.LENGTH_SHORT).show()
        }
        updateWallpaperStatus()
    }

    private fun updateWallpaperStatus() {
        val running = WallpaperChangeWorker.isRunning(this)
        if (running) {
            binding.btnToggleWallpaper.text = getString(R.string.wallpaper_stop)
            binding.btnToggleWallpaper.setBackgroundColor(getColor(R.color.stop_red))
            binding.tvWallpaperStatus.text = getString(R.string.wallpaper_status_running)
            binding.tvWallpaperStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.btnToggleWallpaper.text = getString(R.string.wallpaper_start)
            binding.tvWallpaperStatus.text = getString(R.string.wallpaper_status_stopped)
            binding.tvWallpaperStatus.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    private fun onImagesSelected(uris: List<Uri>) {
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        prefs.addImageUris(uris)
        updateImageDisplay()

        Toast.makeText(
            this,
            "${uris.size}枚追加（合計: ${prefs.imageUris.size}枚）",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onFolderSelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
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
            if (folderUri != null) {
                scope.launch {
                    val folderCount = withContext(Dispatchers.IO) {
                        ImageSource(this@SettingsActivity)
                            .listMedia(folderUri, false).size
                    }
                    binding.tvImageCount.text = getString(R.string.image_count, cloudCount + folderCount)
                }
            } else {
                binding.tvImageCount.text = getString(R.string.image_count, cloudCount)
            }
            binding.tvImageCount.visibility = TextView.VISIBLE
            binding.btnClearImages.visibility = android.view.View.VISIBLE
        } else {
            binding.tvImageCount.text = getString(R.string.no_folder_selected)
            binding.tvImageCount.visibility = TextView.VISIBLE
            binding.btnClearImages.visibility = android.view.View.GONE
        }
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onSelected(position: Int)
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
