package com.mobuk.app.ai.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mobuk.app.MobApp
import com.mobuk.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service so multi-gigabyte model downloads are not killed when the app is backgrounded. */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForegroundCompat(buildNotification(0))
        if (job?.isActive == true) return START_STICKY
        val downloader = (application as MobApp).graph.modelDownloader
        job = scope.launch {
            try {
                downloader.download { p ->
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification((p * 100).toInt()))
                }
            } catch (_: Exception) {
                // State is surfaced through ModelDownloader.state; nothing else to do here.
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.model_download_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(percent: Int): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.model_download_title))
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_ID = 4101
    }
}
