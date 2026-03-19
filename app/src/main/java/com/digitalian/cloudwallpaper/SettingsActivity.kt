package com.digitalian.cloudwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.digitalian.cloudwallpaper.databinding.ActivitySettingsBinding
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: WallpaperPrefs
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        // フォルダ選択
        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(prefs.folderUri)
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
        // フォルダ表示
        updateFolderDisplay()

        // 各Spinnerの初期位置設定
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

    private fun onFolderSelected(uri: Uri) {
        // 永続的なアクセス権限を取得
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        prefs.folderUri = uri
        prefs.folderName = uri.lastPathSegment ?: "Unknown"
        prefs.currentIndex = 0

        updateFolderDisplay()
        countImages(uri)
    }

    private fun updateFolderDisplay() {
        val folderUri = prefs.folderUri
        if (folderUri != null) {
            binding.tvFolderPath.text = getString(R.string.selected_folder, prefs.folderName)
            binding.tvFolderPath.visibility = TextView.VISIBLE
            countImages(folderUri)
        } else {
            binding.tvFolderPath.text = getString(R.string.no_folder_selected)
            binding.tvFolderPath.visibility = TextView.VISIBLE
            binding.tvImageCount.visibility = TextView.GONE
        }
    }

    private fun countImages(uri: Uri) {
        scope.launch {
            binding.tvImageCount.text = getString(R.string.loading)
            binding.tvImageCount.visibility = TextView.VISIBLE

            val count = withContext(Dispatchers.IO) {
                val source = ImageSource(this@SettingsActivity)
                source.listMedia(uri, prefs.includeVideos).size
            }

            binding.tvImageCount.text = getString(R.string.image_count, count)
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

    /** Spinner listener のボイラープレート削減 */
    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onSelected(position: Int)
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
