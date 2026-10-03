package uy.autovideo.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import uy.autovideo.MainActivity
import uy.autovideo.R
import uy.autovideo.media.NowPlaying
import androidx.media.app.NotificationCompat as MediaNotificationCompat

/**
 * Servicio de reproducción en primer plano: mientras la pantalla del auto está abierta, mantiene
 * viva la app para que el audio siga cuando Android Auto oculta el video al manejar.
 * Muestra una notificación con los controles (y el título) de lo que suena.
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            running = false
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType())
            running = true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar el servicio de reproducción", e)
            stopSelf()
            running = false
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaNotificationCompat.MediaStyle().setMediaSession(NowPlaying.session(this).sessionToken))
            .build()
    }

    companion object {
        private const val TAG = "AutoVideo"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "uy.autovideo.STOP_PLAYBACK_SERVICE"

        @Volatile
        var running = false
            private set

        /** Llamar con la pantalla del auto visible (Android no deja iniciarlo desde segundo plano). */
        fun start(context: Context) {
            if (running) return
            try {
                context.startForegroundService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo iniciar el servicio de reproducción", e)
            }
        }

        fun stop(context: Context) {
            if (!running) return
            try {
                context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                context.stopService(Intent(context, PlaybackService::class.java))
            }
            running = false
        }
    }
}
