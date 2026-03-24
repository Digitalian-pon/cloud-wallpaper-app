package com.digitalian.cloudwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * スライドショー設定の管理
 * 画像URIリストはJSON配列として保存
 */
class WallpaperPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IMAGE_URIS = "image_uris"
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

        const val INTERVAL_1MIN = 60_000L
        const val INTERVAL_5MIN = 300_000L
        const val INTERVAL_15MIN = 900_000L
        const val INTERVAL_30MIN = 1_800_000L
        const val INTERVAL_1HOUR = 3_600_000L
        const val INTERVAL_3HOUR = 10_800_000L
        const val INTERVAL_6HOUR = 21_600_000L

        const val TRANSITION_FADE = "fade"
        const val TRANSITION_SLIDE = "slide"
        const val TRANSITION_NONE = "none"

        const val ORDER_RANDOM = "random"
        const val ORDER_SEQUENTIAL = "sequential"
        const val ORDER_DATE_NEWEST = "date_newest"

        const val SCALE_FILL = "fill"
        const val SCALE_FIT = "fit"
    }

    /** 個別選択した画像URIリスト（クラウド対応） */
    var imageUris: List<Uri>
        get() {
            val raw = prefs.getString(KEY_IMAGE_URIS, "") ?: ""
            if (raw.isEmpty()) return emptyList()
            return raw.split("\n").filter { it.isNotEmpty() }.map { Uri.parse(it) }
        }
        set(value) {
            prefs.edit().putString(
                KEY_IMAGE_URIS,
                value.joinToString("\n") { it.toString() }
            ).apply()
        }

    fun addImageUris(newUris: List<Uri>) {
        val current = imageUris.toMutableList()
        val existing = current.map { it.toString() }.toSet()
        for (uri in newUris) {
            if (uri.toString() !in existing) {
                current.add(uri)
            }
        }
        imageUris = current
    }

    fun removeImageUri(uri: Uri) {
        imageUris = imageUris.filter { it != uri }
    }

    fun clearImageUris() {
        imageUris = emptyList()
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

    /** 全設定をJSONにエクスポート */
    fun exportToJson(): String {
        val json = JSONObject()
        json.put(KEY_IMAGE_URIS, JSONArray(imageUris.map { it.toString() }))
        json.put(KEY_FOLDER_URI, folderUri?.toString() ?: "")
        json.put(KEY_FOLDER_NAME, folderName)
        json.put(KEY_INTERVAL_MS, intervalMs)
        json.put(KEY_TRANSITION, transition)
        json.put(KEY_ORDER, order)
        json.put(KEY_SCALE_MODE, scaleMode)
        json.put(KEY_INCLUDE_VIDEOS, includeVideos)
        json.put(KEY_MUTE_VIDEOS, muteVideos)
        json.put(KEY_VIDEO_DURATION_SEC, videoDurationSec)
        json.put(KEY_CURRENT_INDEX, currentIndex)
        json.put(KEY_SHUFFLE_SEED, shuffleSeed)
        return json.toString(2)
    }

    /** JSONから全設定をインポート */
    fun importFromJson(jsonString: String) {
        val json = JSONObject(jsonString)

        // 画像URI
        val uriArray = json.optJSONArray(KEY_IMAGE_URIS)
        if (uriArray != null) {
            val uris = mutableListOf<Uri>()
            for (i in 0 until uriArray.length()) {
                val s = uriArray.getString(i)
                if (s.isNotEmpty()) uris.add(Uri.parse(s))
            }
            imageUris = uris
        }

        // フォルダURI
        val folder = json.optString(KEY_FOLDER_URI, "")
        folderUri = if (folder.isNotEmpty()) Uri.parse(folder) else null
        folderName = json.optString(KEY_FOLDER_NAME, "")

        // 設定値
        intervalMs = json.optLong(KEY_INTERVAL_MS, INTERVAL_15MIN)
        transition = json.optString(KEY_TRANSITION, TRANSITION_FADE)
        order = json.optString(KEY_ORDER, ORDER_RANDOM)
        scaleMode = json.optString(KEY_SCALE_MODE, SCALE_FILL)
        includeVideos = json.optBoolean(KEY_INCLUDE_VIDEOS, false)
        muteVideos = json.optBoolean(KEY_MUTE_VIDEOS, true)
        videoDurationSec = json.optInt(KEY_VIDEO_DURATION_SEC, 10)
        currentIndex = json.optInt(KEY_CURRENT_INDEX, 0)
        shuffleSeed = json.optLong(KEY_SHUFFLE_SEED, System.currentTimeMillis())
    }
}
