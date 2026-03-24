package com.digitalian.cloudwallpaper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlin.math.max
import kotlin.random.Random

/**
 * 短い間隔（15分未満）の壁紙スライドショー用 Foreground Service
 * WorkManager は最小15分制限があるため、1分・5分間隔にはこちらを使う
 */
class WallpaperSlideshowService : Service() {

    companion object {
        private const val TAG = "WallpaperSlideshow"
        private const val CHANNEL_ID = "wallpaper_slideshow"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, WallpaperSlideshowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WallpaperSlideshowService::class.java))
        }

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (service.service.className == WallpaperSlideshowService::class.java.name) {
                    return true
                }
            }
            return false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: WallpaperPrefs

    private val changeRunnable = object : Runnable {
        override fun run() {
            changeWallpaper()
            handler.postDelayed(this, prefs.intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = WallpaperPrefs(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // 即座に1回変更してからタイマー開始
        handler.removeCallbacks(changeRunnable)
        changeWallpaper()
        handler.postDelayed(changeRunnable, prefs.intervalMs)

        Log.d(TAG, "Service started: interval=${prefs.intervalMs / 1000}sec")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(changeRunnable)
        Log.d(TAG, "Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun changeWallpaper() {
        try {
            val allUris = getAllImageUris()
            if (allUris.isEmpty()) {
                Log.w(TAG, "No images available")
                return
            }

            val index = when (prefs.order) {
                WallpaperPrefs.ORDER_RANDOM -> Random.nextInt(allUris.size)
                else -> {
                    val next = (prefs.currentIndex + 1) % allUris.size
                    prefs.currentIndex = next
                    next
                }
            }

            setWallpaperFromUri(allUris[index])
            Log.d(TAG, "Wallpaper changed: ${index + 1}/${allUris.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change wallpaper", e)
        }
    }

    private fun getAllImageUris(): List<Uri> {
        val uris = mutableListOf<Uri>()
        uris.addAll(prefs.imageUris)

        val folderUri = prefs.folderUri
        if (folderUri != null) {
            try {
                val source = ImageSource(this)
                val folderItems = source.listMedia(folderUri, false)
                uris.addAll(folderItems.map { it.uri })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read folder", e)
            }
        }
        return uris
    }

    private fun setWallpaperFromUri(uri: Uri) {
        val wm = android.app.WallpaperManager.getInstance(this)
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }

        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, screenW, screenH)
        opts.inJustDecodeBounds = false

        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return

        val finalBitmap = if (prefs.scaleMode == WallpaperPrefs.SCALE_FILL) {
            centerCrop(bitmap, screenW, screenH)
        } else {
            fitCenter(bitmap, screenW, screenH)
        }

        wm.setBitmap(
            finalBitmap, null, true,
            android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
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
        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(-x, -y, -x + scaledW, -y + scaledH), null)
        return result
    }

    private fun fitCenter(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val dstW = (src.width * scale).toInt()
        val dstH = (src.height * scale).toInt()
        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        val left = (targetW - dstW) / 2
        val top = (targetH - dstH) / 2
        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(left, top, left + dstW, top + dstH), null)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "壁紙スライドショー",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "壁紙の自動切替を実行中"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalText = when (prefs.intervalMs) {
            WallpaperPrefs.INTERVAL_1MIN -> "1分"
            WallpaperPrefs.INTERVAL_5MIN -> "5分"
            else -> "${prefs.intervalMs / 60_000}分"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("壁紙スライドショー実行中")
            .setContentText("${intervalText}ごとに壁紙を切り替え中")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
