package com.digitalian.cloudwallpaper

import android.app.Application

class CloudWallpaperApp : Application() {
    companion object {
        lateinit var instance: CloudWallpaperApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
