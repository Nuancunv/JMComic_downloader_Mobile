package com.nuancun.jmcomic.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class JMComicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.download_channel_description) }
        val exportChannel = NotificationChannel(
            EXPORT_CHANNEL_ID,
            getString(R.string.export_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.export_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(downloadChannel, exportChannel))
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "downloads"
        const val EXPORT_CHANNEL_ID = "exports"
    }
}
