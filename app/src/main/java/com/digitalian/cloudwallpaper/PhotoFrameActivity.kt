package com.digitalian.cloudwallpaper

import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.random.Random

/**
 * フォトフレームモード
 * - 画面常時ON (FLAG_KEEP_SCREEN_ON)
 * - フルスクリーン（ステータスバー・ナビバー非表示）
 * - スライドショー with トランジション
 * - タップで時計/情報のオーバーレイ表示
 * - ダブルタップで終了
 */
class PhotoFrameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhotoFrame"
    }

    private lateinit var prefs: WallpaperPrefs
    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var clockOverlay: View
    private lateinit var tvClock: TextView
    private lateinit var tvImageInfo: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var imageUris: List<Uri> = emptyList()
    private var currentIndex = 0
    private var showingFirst = true  // imageView1が前面
    private var overlayVisible = false
    private var lastTapTime = 0L

    private val slideshowRunnable = object : Runnable {
        override fun run() {
            advanceToNext()
            handler.postDelayed(this, prefs.intervalMs)
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    private val hideOverlayRunnable = Runnable {
        hideOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_frame)

        prefs = WallpaperPrefs(this)

        imageView1 = findViewById(R.id.imageView1)
        imageView2 = findViewById(R.id.imageView2)
        clockOverlay = findViewById(R.id.clockOverlay)
        tvClock = findViewById(R.id.tvClock)
        tvImageInfo = findViewById(R.id.tvImageInfo)

        // 画面常時ON
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // フルスクリーン（イマーシブモード）
        enterImmersiveMode()

        // タップ処理
        val rootView = findViewById<View>(R.id.rootFrame)
        rootView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 300) {
                // ダブルタップ → 終了
                finish()
            } else {
                // シングルタップ → オーバーレイ表示/非表示
                lastTapTime = now
                handler.postDelayed({
                    if (System.currentTimeMillis() - lastTapTime >= 280) {
                        toggleOverlay()
                    }
                }, 320)
            }
        }

        // スワイプで次/前の画像
        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startX = event.x
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - startX
                        if (dx > 150) {
                            // 右スワイプ → 前の画像
                            goToPrevious()
                            resetTimer()
                            return true
                        } else if (dx < -150) {
                            // 左スワイプ → 次の画像
                            advanceToNext()
                            resetTimer()
                            return true
                        }
                    }
                }
                return false
            }
        })

        loadImages()
        startSlideshow()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun loadImages() {
        val allUris = mutableListOf<Uri>()
        allUris.addAll(prefs.imageUris)

        val folderUri = prefs.folderUri
        if (folderUri != null) {
            try {
                val source = ImageSource(this)
                val folderItems = source.listMedia(folderUri, false)
                allUris.addAll(folderItems.map { it.uri })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load folder images", e)
            }
        }

        imageUris = when (prefs.order) {
            WallpaperPrefs.ORDER_RANDOM -> allUris.shuffled(Random(prefs.shuffleSeed))
            else -> allUris
        }

        currentIndex = 0
    }

    private fun startSlideshow() {
        if (imageUris.isEmpty()) {
            tvImageInfo.text = "画像が選択されていません\n設定から画像を追加してください"
            tvImageInfo.visibility = View.VISIBLE
            return
        }

        // 最初の画像を表示
        showImage(imageUris[0], imageView1)
        imageView1.alpha = 1f
        imageView2.alpha = 0f
        showingFirst = true

        // スライドショー開始
        handler.postDelayed(slideshowRunnable, prefs.intervalMs)
    }

    private fun advanceToNext() {
        if (imageUris.isEmpty()) return
        currentIndex = (currentIndex + 1) % imageUris.size
        showWithTransition()
    }

    private fun goToPrevious() {
        if (imageUris.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else imageUris.size - 1
        showWithTransition()
    }

    private fun showWithTransition() {
        val uri = imageUris[currentIndex]
        val frontView = if (showingFirst) imageView1 else imageView2
        val backView = if (showingFirst) imageView2 else imageView1

        // 背面に次の画像をロード
        showImage(uri, backView)
        backView.alpha = 0f

        val duration = when (prefs.transition) {
            WallpaperPrefs.TRANSITION_NONE -> 0L
            else -> 1000L
        }

        if (duration == 0L) {
            frontView.alpha = 0f
            backView.alpha = 1f
            showingFirst = !showingFirst
        } else {
            // クロスフェード
            backView.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()

            frontView.animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    showingFirst = !showingFirst
                }
                .start()
        }
    }

    private fun showImage(uri: Uri, imageView: ImageView) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadBitmap(uri)
            }
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = when (prefs.scaleMode) {
                    WallpaperPrefs.SCALE_FIT -> ImageView.ScaleType.FIT_CENTER
                    else -> ImageView.ScaleType.CENTER_CROP
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            options.inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight, screenW, screenH
            )
            options.inJustDecodeBounds = false

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load: $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
        var size = 1
        if (rawH > reqH || rawW > reqW) {
            val halfH = rawH / 2
            val halfW = rawW / 2
            while (halfH / size >= reqH && halfW / size >= reqW) {
                size *= 2
            }
        }
        return size
    }

    private fun resetTimer() {
        handler.removeCallbacks(slideshowRunnable)
        handler.postDelayed(slideshowRunnable, prefs.intervalMs)
    }

    private fun toggleOverlay() {
        if (overlayVisible) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        overlayVisible = true
        updateClock()
        clockOverlay.visibility = View.VISIBLE
        clockOverlay.animate().alpha(1f).setDuration(300).start()
        handler.post(clockRunnable)

        tvImageInfo.text = "${currentIndex + 1} / ${imageUris.size}"

        // 5秒後に自動で消す
        handler.removeCallbacks(hideOverlayRunnable)
        handler.postDelayed(hideOverlayRunnable, 5000)
    }

    private fun hideOverlay() {
        overlayVisible = false
        clockOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            clockOverlay.visibility = View.GONE
        }.start()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(hideOverlayRunnable)
    }

    private fun updateClock() {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        tvClock.text = sdf.format(java.util.Date())
    }
}
