package com.digitalian.cloudwallpaper

import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import kotlin.math.min
import kotlin.math.max
import kotlin.random.Random

/**
 * ライブ壁紙サービス
 * クラウドストレージ・ローカルの画像をスライドショーで表示
 */
class CloudWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "CloudWallpaper"
    }

    override fun onCreateEngine(): Engine = SlideShowEngine()

    inner class SlideShowEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs by lazy { WallpaperPrefs(this@CloudWallpaperService) }
        private val imageSource by lazy { ImageSource(this@CloudWallpaperService) }

        private var imageUris: List<Uri> = emptyList()
        private var currentIndex = 0
        private var currentBitmap: Bitmap? = null
        private var nextBitmap: Bitmap? = null
        private var transitionProgress = -1f
        private var isVisible = false
        private var screenWidth = 0
        private var screenHeight = 0

        private val transitionDuration = 1000L
        private val transitionStepMs = 16L
        private var transitionStartTime = 0L

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (!isVisible) return
                advanceToNext()
                handler.postDelayed(this, prefs.intervalMs)
            }
        }

        private val transitionRunnable = object : Runnable {
            override fun run() {
                if (!isVisible || transitionProgress < 0f) return

                val elapsed = System.currentTimeMillis() - transitionStartTime
                transitionProgress = min(1f, elapsed.toFloat() / transitionDuration)

                drawTransitionFrame()

                if (transitionProgress >= 1f) {
                    currentBitmap?.recycle()
                    currentBitmap = nextBitmap
                    nextBitmap = null
                    transitionProgress = -1f
                    drawCurrentFrame()
                } else {
                    handler.postDelayed(this, transitionStepMs)
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width
            screenHeight = height
            loadImageList()
            if (imageUris.isNotEmpty()) {
                currentBitmap = loadBitmap(imageUris[currentIndex])
                drawCurrentFrame()
            } else {
                drawPlaceholder()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                loadImageList()
                if (currentBitmap == null && imageUris.isNotEmpty()) {
                    currentBitmap = loadBitmap(imageUris[currentIndex])
                }
                if (imageUris.isNotEmpty()) {
                    drawCurrentFrame()
                } else {
                    drawPlaceholder()
                }
                scheduleNext()
            } else {
                handler.removeCallbacks(drawRunnable)
                handler.removeCallbacks(transitionRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(transitionRunnable)
            currentBitmap?.recycle()
            nextBitmap?.recycle()
            currentBitmap = null
            nextBitmap = null
        }

        /**
         * 画像リストを構築:
         * 1. 個別選択した画像URI（クラウド含む）
         * 2. フォルダ内の画像（ローカル）
         */
        private fun loadImageList() {
            try {
                val allUris = mutableListOf<Uri>()

                // クラウド/個別選択の画像
                allUris.addAll(prefs.imageUris)

                // ローカルフォルダの画像
                val folderUri = prefs.folderUri
                if (folderUri != null) {
                    val folderItems = imageSource.listMedia(folderUri, false)
                    allUris.addAll(folderItems.map { it.uri })
                }

                // 並び替え
                imageUris = when (prefs.order) {
                    WallpaperPrefs.ORDER_RANDOM -> allUris.shuffled(Random(prefs.shuffleSeed))
                    else -> allUris // 選択順 or そのまま
                }

                currentIndex = prefs.currentIndex.coerceIn(0, max(0, imageUris.size - 1))
                Log.d(TAG, "Loaded ${imageUris.size} images (cloud: ${prefs.imageUris.size})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image list", e)
            }
        }

        private fun loadBitmap(uri: Uri): Bitmap? {
            return try {
                // まずサイズだけ取得
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                // サンプルサイズ計算（メモリ節約）
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth, options.outHeight,
                    screenWidth, screenHeight
                )
                options.inJustDecodeBounds = false

                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap: $uri", e)
                null
            }
        }

        private fun calculateInSampleSize(
            rawWidth: Int, rawHeight: Int,
            reqWidth: Int, reqHeight: Int
        ): Int {
            var inSampleSize = 1
            if (rawHeight > reqHeight || rawWidth > reqWidth) {
                val halfHeight = rawHeight / 2
                val halfWidth = rawWidth / 2
                while (halfHeight / inSampleSize >= reqHeight &&
                    halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun scheduleNext() {
            handler.removeCallbacks(drawRunnable)
            if (imageUris.isNotEmpty()) {
                handler.postDelayed(drawRunnable, prefs.intervalMs)
            }
        }

        private fun advanceToNext() {
            if (imageUris.isEmpty()) {
                drawPlaceholder()
                return
            }

            currentIndex = (currentIndex + 1) % imageUris.size
            prefs.currentIndex = currentIndex

            val newBitmap = loadBitmap(imageUris[currentIndex])
            if (newBitmap == null) {
                scheduleNext()
                return
            }

            when (prefs.transition) {
                WallpaperPrefs.TRANSITION_FADE,
                WallpaperPrefs.TRANSITION_SLIDE -> {
                    nextBitmap = newBitmap
                    transitionStartTime = System.currentTimeMillis()
                    transitionProgress = 0f
                    handler.post(transitionRunnable)
                }
                else -> {
                    currentBitmap?.recycle()
                    currentBitmap = newBitmap
                    drawCurrentFrame()
                }
            }
        }

        private fun drawCurrentFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    currentBitmap?.let { drawScaledBitmap(canvas, it, 255) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Draw error", e)
            } finally {
                canvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
            }
        }

        private fun drawTransitionFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)

                    when (prefs.transition) {
                        WallpaperPrefs.TRANSITION_FADE -> {
                            currentBitmap?.let {
                                drawScaledBitmap(canvas, it, (255 * (1f - transitionProgress)).toInt())
                            }
                            nextBitmap?.let {
                                drawScaledBitmap(canvas, it, (255 * transitionProgress).toInt())
                            }
                        }
                        WallpaperPrefs.TRANSITION_SLIDE -> {
                            val offsetX = (-screenWidth * transitionProgress).toInt()
                            currentBitmap?.let {
                                drawScaledBitmap(canvas, it, 255, offsetX = offsetX)
                            }
                            nextBitmap?.let {
                                drawScaledBitmap(canvas, it, 255, offsetX = offsetX + screenWidth)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transition draw error", e)
            } finally {
                canvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
            }
        }

        private fun drawScaledBitmap(
            canvas: Canvas,
            bitmap: Bitmap,
            alpha: Int,
            offsetX: Int = 0
        ) {
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                this.alpha = alpha.coerceIn(0, 255)
            }

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val sw = screenWidth.toFloat()
            val sh = screenHeight.toFloat()

            val srcRect: Rect
            val dstRect: Rect

            if (prefs.scaleMode == WallpaperPrefs.SCALE_FILL) {
                val scale = max(sw / bw, sh / bh)
                val scaledW = (sw / scale).toInt()
                val scaledH = (sh / scale).toInt()
                val x = ((bw - scaledW) / 2).toInt()
                val y = ((bh - scaledH) / 2).toInt()
                srcRect = Rect(x, y, x + scaledW, y + scaledH)
                dstRect = Rect(offsetX, 0, offsetX + screenWidth, screenHeight)
            } else {
                val scale = min(sw / bw, sh / bh)
                val dstW = (bw * scale).toInt()
                val dstH = (bh * scale).toInt()
                val x = ((sw - dstW) / 2).toInt() + offsetX
                val y = ((sh - dstH) / 2).toInt()
                srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                dstRect = Rect(x, y, x + dstW, y + dstH)
            }

            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }

        private fun drawPlaceholder() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.parseColor("#1a1a2e"))

                    val paint = Paint().apply {
                        color = Color.WHITE
                        textSize = 48f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText(
                        "Cloud Wallpaper",
                        screenWidth / 2f,
                        screenHeight / 2f - 40,
                        paint
                    )

                    paint.textSize = 32f
                    paint.color = Color.GRAY
                    canvas.drawText(
                        "設定から画像を選択してください",
                        screenWidth / 2f,
                        screenHeight / 2f + 40,
                        paint
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Placeholder draw error", e)
            } finally {
                canvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
            }
        }
    }
}
