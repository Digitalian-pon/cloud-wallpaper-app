package com.digitalian.cloudwallpaper

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * インターネットTVモード
 * - フルスクリーンWebView
 * - 画面常時ON
 * - 動画フルスクリーン対応
 * - よく使うチャンネルのブックマーク
 */
class InternetTvActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var channelBar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnChannels: ImageButton

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var channelBarVisible = true

    companion object {
        const val EXTRA_URL = "extra_url"

        val CHANNELS = listOf(
            Channel("YouTube", "https://m.youtube.com"),
            Channel("AbemaTV", "https://abema.tv"),
            Channel("TVer", "https://tver.jp"),
            Channel("NHK+", "https://plus.nhk.jp"),
            Channel("GYAO", "https://gyao.yahoo.co.jp"),
            Channel("ニコニコ", "https://sp.nicovideo.jp"),
            Channel("Twitch", "https://m.twitch.tv"),
            Channel("Pluto TV", "https://pluto.tv"),
        )
    }

    data class Channel(val name: String, val url: String)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_internet_tv)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webView)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        channelBar = findViewById(R.id.channelBar)
        btnBack = findViewById(R.id.btnBack)
        btnChannels = findViewById(R.id.btnChannels)

        setupWebView()
        setupChannelBar()
        enterImmersiveMode()

        val url = intent.getStringExtra(EXTRA_URL) ?: CHANNELS[0].url
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = webView.settings.userAgentString.replace("; wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // 動画フルスクリーン対応
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                fullscreenView = view
                fullscreenCallback = callback

                webView.visibility = View.GONE
                channelBar.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view)

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                enterImmersiveMode()
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeView(fullscreenView)
                fullscreenContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE

                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (channelBarVisible) channelBar.visibility = View.VISIBLE
                enterImmersiveMode()
            }
        }
    }

    private fun setupChannelBar() {
        val container = findViewById<LinearLayout>(R.id.channelButtonsContainer)

        for (channel in CHANNELS) {
            val btn = com.google.android.material.chip.Chip(this).apply {
                text = channel.name
                isClickable = true
                setOnClickListener {
                    webView.loadUrl(channel.url)
                }
                setTextColor(resources.getColor(android.R.color.white, theme))
                setChipBackgroundColorResource(R.color.channel_chip_bg)
                chipMinHeight = 36f
            }
            container.addView(btn)
        }

        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        btnChannels.setOnClickListener {
            channelBarVisible = !channelBarVisible
            val scrollView = findViewById<android.widget.HorizontalScrollView>(R.id.channelScroll)
            scrollView.visibility = if (channelBarVisible) View.VISIBLE else View.GONE
        }

        // タップでチャンネルバー表示/非表示
        webView.setOnTouchListener { _, _ -> false }
    }

    override fun onBackPressed() {
        if (fullscreenView != null) {
            fullscreenCallback?.onCustomViewHidden()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
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

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
