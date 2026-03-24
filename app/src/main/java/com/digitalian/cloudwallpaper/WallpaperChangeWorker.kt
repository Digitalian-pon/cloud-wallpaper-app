package com.digitalian.cloudwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

/**
 * WorkManagerで定期的に壁紙を切り替えるWorker
 * ボタン一発で開始/停止 - ライブ壁紙の面倒な設定は不要
 */
class WallpaperChangeWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "WallpaperWorker"
        private const val WORK_NAME = "wallpaper_slideshow"

        private const val SHORT_INTERVAL_THRESHOLD = 900_000L // 15分

        /** スライドショー開始（短い間隔はForeground Service、15分以上はWorkManager） */
        fun start(context: Context, intervalMs: Long) {
            if (intervalMs < SHORT_INTERVAL_THRESHOLD) {
                // 短い間隔 → Foreground Service を使用
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                WallpaperSlideshowService.start(context)
                Log.d(TAG, "Wallpaper slideshow started (Service): interval=${intervalMs / 1000}sec")
            } else {
                // 15分以上 → WorkManager を使用
                WallpaperSlideshowService.stop(context)

                val oneTimeRequest = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(oneTimeRequest)

                val intervalMin = intervalMs / 60_000
                val periodicRequest = PeriodicWorkRequestBuilder<WallpaperChangeWorker>(
                    intervalMin, TimeUnit.MINUTES
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        periodicRequest
                    )

                Log.d(TAG, "Wallpaper slideshow started (WorkManager): interval=${intervalMin}min")
            }
        }

        /** スライドショー停止（両方停止） */
        fun stop(context: Context) {
            WallpaperSlideshowService.stop(context)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Wallpaper slideshow stopped")
        }

        /** 実行中かチェック（Service または WorkManager） */
        fun isRunning(context: Context): Boolean {
            if (WallpaperSlideshowService.isRunning(context)) return true
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            return workInfos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }
        }
    }

    override fun doWork(): Result {
        return try {
            val prefs = WallpaperPrefs(applicationContext)
            val allUris = getAllImageUris(prefs)

            if (allUris.isEmpty()) {
                Log.w(TAG, "No images available")
                return Result.success()
            }

            // 次の画像を選択
            val index = when (prefs.order) {
                WallpaperPrefs.ORDER_RANDOM -> Random.nextInt(allUris.size)
                else -> {
                    val next = (prefs.currentIndex + 1) % allUris.size
                    prefs.currentIndex = next
                    next
                }
            }

            val uri = allUris[index]
            setWallpaperFromUri(uri, prefs)

            Log.d(TAG, "Wallpaper changed: ${index + 1}/${allUris.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change wallpaper", e)
            Result.retry()
        }
    }

    private fun getAllImageUris(prefs: WallpaperPrefs): List<Uri> {
        val uris = mutableListOf<Uri>()
        uris.addAll(prefs.imageUris)

        val folderUri = prefs.folderUri
        if (folderUri != null) {
            try {
                val source = ImageSource(applicationContext)
                val folderItems = source.listMedia(folderUri, false)
                uris.addAll(folderItems.map { it.uri })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read folder", e)
            }
        }
        return uris
    }

    private fun setWallpaperFromUri(uri: Uri, prefs: WallpaperPrefs) {
        val wm = WallpaperManager.getInstance(applicationContext)
        val displayMetrics = applicationContext.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        // サイズ取得
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }

        // サンプリング
        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, screenW, screenH)
        opts.inJustDecodeBounds = false

        val bitmap = applicationContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return

        // スケーリング
        val finalBitmap = if (prefs.scaleMode == WallpaperPrefs.SCALE_FILL) {
            centerCrop(bitmap, screenW, screenH)
        } else {
            fitCenter(bitmap, screenW, screenH)
        }

        // ホーム画面 + ロック画面の両方に設定
        wm.setBitmap(
            finalBitmap,
            null,
            true,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        )

        if (finalBitmap != bitmap) bitmap.recycle()
        finalBitmap.recycle()
    }

    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = max(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()
        val x = (scaledW - targetW) / 2
        val y = (scaledH - targetH) / 2

        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = Rect(-x, -y, -x + scaledW, -y + scaledH)
        canvas.drawBitmap(src, srcRect, dstRect, null)
        return result
    }

    private fun fitCenter(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val dstW = (src.width * scale).toInt()
        val dstH = (src.height * scale).toInt()

        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        val left = (targetW - dstW) / 2
        val top = (targetH - dstH) / 2
        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = Rect(left, top, left + dstW, top + dstH)
        canvas.drawBitmap(src, srcRect, dstRect, null)
        return result
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
}
