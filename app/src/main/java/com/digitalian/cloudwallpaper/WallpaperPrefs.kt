package com.digitalian.cloudwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * スライドショー設定の管理
 */
class WallpaperPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_FOLDER_NAME = "folder_name"
        private const val KEY_INTERVAL_MS = "interval_ms"
        private const val KEY_TRANSITION = "transition"
        private const val KEY_ORDER = "order"
        private const val KEY_SCALE_MODE = "scale_mode"
        private const val KEY_INCLUDE_VIDEOS = "include_videos"
        private const val KEY_MUTE_VIDEOS = "mute_videos"
        private const val KEY_VIDEO_DURATION_SEC = "video_duration_sec"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_SHUFFLE_SEED = "shuffle_seed"

        // 切替間隔（ミリ秒）
        const val INTERVAL_1MIN = 60_000L
        const val INTERVAL_5MIN = 300_000L
        const val INTERVAL_15MIN = 900_000L
        const val INTERVAL_30MIN = 1_800_000L
        const val INTERVAL_1HOUR = 3_600_000L
        const val INTERVAL_3HOUR = 10_800_000L
        const val INTERVAL_6HOUR = 21_600_000L

        // トランジション
        const val TRANSITION_FADE = "fade"
        const val TRANSITION_SLIDE = "slide"
        const val TRANSITION_NONE = "none"

        // 表示順序
        const val ORDER_RANDOM = "random"
        const val ORDER_SEQUENTIAL = "sequential"
        const val ORDER_DATE_NEWEST = "date_newest"

        // スケーリング
        const val SCALE_FILL = "fill"
        const val SCALE_FIT = "fit"
    }

    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_FOLDER_URI, value?.toString()).apply()

    var folderName: String
        get() = prefs.getString(KEY_FOLDER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FOLDER_NAME, value).apply()

    var intervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL_MS, INTERVAL_15MIN)
        set(value) = prefs.edit().putLong(KEY_INTERVAL_MS, value).apply()

    var transition: String
        get() = prefs.getString(KEY_TRANSITION, TRANSITION_FADE) ?: TRANSITION_FADE
        set(value) = prefs.edit().putString(KEY_TRANSITION, value).apply()

    var order: String
        get() = prefs.getString(KEY_ORDER, ORDER_RANDOM) ?: ORDER_RANDOM
        set(value) = prefs.edit().putString(KEY_ORDER, value).apply()

    var scaleMode: String
        get() = prefs.getString(KEY_SCALE_MODE, SCALE_FILL) ?: SCALE_FILL
        set(value) = prefs.edit().putString(KEY_SCALE_MODE, value).apply()

    var includeVideos: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_VIDEOS, false)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_VIDEOS, value).apply()

    var muteVideos: Boolean
        get() = prefs.getBoolean(KEY_MUTE_VIDEOS, true)
        set(value) = prefs.edit().putBoolean(KEY_MUTE_VIDEOS, value).apply()

    var videoDurationSec: Int
        get() = prefs.getInt(KEY_VIDEO_DURATION_SEC, 10)
        set(value) = prefs.edit().putInt(KEY_VIDEO_DURATION_SEC, value).apply()

    var currentIndex: Int
        get() = prefs.getInt(KEY_CURRENT_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_CURRENT_INDEX, value).apply()

    var shuffleSeed: Long
        get() = prefs.getLong(KEY_SHUFFLE_SEED, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_SHUFFLE_SEED, value).apply()
}
