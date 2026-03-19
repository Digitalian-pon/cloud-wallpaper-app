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
 * クラウドストレージの画像をスライドショーで表示
 */
class CloudWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "CloudWallpaper"
        // 外部から設定変更を通知するためのコールバック
        var onSettingsChanged: (() -> Unit)? = null
    }

    override fun onCreateEngine(): Engine = SlideShowEngine()

    inner class SlideShowEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs by lazy { WallpaperPrefs(this@CloudWallpaperService) }
        private val imageSource by lazy { ImageSource(this@CloudWallpaperService) }

        private var mediaItems: List<ImageSource.MediaItem> = emptyList()
        private var currentIndex = 0
        private var currentBitmap: Bitmap? = null
        private var nextBitmap: Bitmap? = null
        private var transitionProgress = -1f  // -1 = not in transition
        private var isVisible = false
        private var screenWidth = 0
        private var screenHeight = 0

        private val transitionDuration = 1000L  // 1秒のフェード
        private val transitionStepMs = 16L  // ~60fps
        private var transitionStartTime = 0L

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (!isVisible) return
                drawFrame()
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
                    // トランジション完了
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
            loadMediaList()
            if (mediaItems.isNotEmpty()) {
                currentBitmap = loadBitmap(mediaItems[currentIndex].uri)
                drawCurrentFrame()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                loadMediaList()
                if (currentBitmap == null && mediaItems.isNotEmpty()) {
                    currentBitmap = loadBitmap(mediaItems[currentIndex].uri)
                }
                drawCurrentFrame()
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

        private fun loadMediaList() {
            val folderUri = prefs.folderUri ?: return
            try {
                mediaItems = imageSource.listMedia(folderUri, prefs.includeVideos)
                    .filter { !it.isVideo }  // 壁紙サービスでは静止画のみ

                mediaItems = when (prefs.order) {
                    WallpaperPrefs.ORDER_RANDOM -> mediaItems.shuffled(Random(prefs.shuffleSeed))
                    WallpaperPrefs.ORDER_DATE_NEWEST -> mediaItems.sortedByDescending { it.lastModified }
                    else -> mediaItems.sortedBy { it.name }
                }

                currentIndex = prefs.currentIndex.coerceIn(0, max(0, mediaItems.size - 1))
                Log.d(TAG, "Loaded ${mediaItems.size} images from folder")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load media list", e)
            }
        }

        private fun loadBitmap(uri: Uri): Bitmap? {
            return try {
                val inputStream = contentResolver.openInputStream(uri) ?: return null

                // まずサイズだけ取得
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                val tempStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(tempStream, null, options)
                tempStream?.close()

                // サンプルサイズ計算（メモリ節約）
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth, options.outHeight,
                    screenWidth, screenHeight
                )
                options.inJustDecodeBounds = false

                val stream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                stream?.close()
                inputStream.close()

                bitmap
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
            if (mediaItems.isNotEmpty()) {
                handler.postDelayed(drawRunnable, prefs.intervalMs)
            }
        }

        /** 次の画像へ移行 */
        private fun drawFrame() {
            if (mediaItems.isEmpty()) {
                drawPlaceholder()
                return
            }

            currentIndex = (currentIndex + 1) % mediaItems.size
            prefs.currentIndex = currentIndex

            val newBitmap = loadBitmap(mediaItems[currentIndex].uri)
            if (newBitmap == null) {
                // 読み込み失敗、次へスキップ
                scheduleNext()
                return
            }

            when (prefs.transition) {
                WallpaperPrefs.TRANSITION_FADE -> {
                    nextBitmap = newBitmap
                    transitionStartTime = System.currentTimeMillis()
                    transitionProgress = 0f
                    handler.post(transitionRunnable)
                }
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

            scheduleNext()
        }

        /** 現在の画像を描画 */
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

        /** トランジション中のフレーム描画 */
        private fun drawTransitionFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)

                    when (prefs.transition) {
                        WallpaperPrefs.TRANSITION_FADE -> {
                            // 現在の画像（フェードアウト）
                            currentBitmap?.let {
                                drawScaledBitmap(canvas, it, (255 * (1f - transitionProgress)).toInt())
                            }
                            // 次の画像（フェードイン）
                            nextBitmap?.let {
                                drawScaledBitmap(canvas, it, (255 * transitionProgress).toInt())
                            }
                        }
                        WallpaperPrefs.TRANSITION_SLIDE -> {
                            val offsetX = (-screenWidth * transitionProgress).toInt()
                            // 現在の画像（左へスライドアウト）
                            currentBitmap?.let {
                                drawScaledBitmap(canvas, it, 255, offsetX = offsetX)
                            }
                            // 次の画像（右からスライドイン）
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

        /** ビットマップをスケーリングして描画 */
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
                // Center crop
                val scale = max(sw / bw, sh / bh)
                val scaledW = (sw / scale).toInt()
                val scaledH = (sh / scale).toInt()
                val x = ((bw - scaledW) / 2).toInt()
                val y = ((bh - scaledH) / 2).toInt()
                srcRect = Rect(x, y, x + scaledW, y + scaledH)
                dstRect = Rect(offsetX, 0, offsetX + screenWidth, screenHeight)
            } else {
                // Fit inside
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

        /** フォルダ未選択時のプレースホルダー */
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
                        "設定からフォルダを選択してください",
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
