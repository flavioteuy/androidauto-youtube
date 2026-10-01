package uy.autovideo.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sesión de reproducción que Android Auto lee para mostrar en el tablero del auto el título,
 * el canal y el tiempo del video, y para recibir los botones del volante.
 * Los datos los manda la página de YouTube (assets/youtube_tweaks.js) a través de CarPlayerActivity.
 */
object NowPlaying {

    /** Lo implementa la pantalla del auto: ejecuta la acción en el reproductor de YouTube. */
    interface Controller {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun seekTo(positionMs: Long)
    }

    var controller: Controller? = null

    private const val TAG = "AutoVideo"
    private const val MEDIA_ID = "current"
    private const val ART_MAX_PX = 512

    private const val ACTIONS = PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_STOP or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_SEEK_TO or
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    private val main = Handler(Looper.getMainLooper())
    private val artLoader = Executors.newSingleThreadExecutor()

    private var session: MediaSessionCompat? = null
    private var title = ""
    private var artist = ""
    private var durationMs = 0L
    private var artUrl = ""
    private var art: Bitmap? = null

    /** Si el usuario apagó "Mostrar en el tablero", el servicio queda desactivado. */
    fun isEnabled(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(
            ComponentName(context, NowPlayingService::class.java)
        )
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, NowPlayingService::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        if (!enabled) stop()
    }

    fun session(context: Context): MediaSessionCompat {
        session?.let { return it }
        val s = MediaSessionCompat(context.applicationContext, "AutoYouTube")
        s.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { controller?.play() }
            override fun onPause() { controller?.pause() }
            override fun onStop() { controller?.pause() }
            override fun onSkipToNext() { controller?.next() }
            override fun onSkipToPrevious() { controller?.previous() }
            override fun onSeekTo(pos: Long) { controller?.seekTo(pos) }
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { controller?.play() }
        })
        s.setPlaybackState(state(PlaybackStateCompat.STATE_NONE, 0L, 0f))
        session = s
        return s
    }

    /** Llamar en el hilo principal con lo que informa la página. */
    fun update(
        context: Context,
        newTitle: String,
        newArtist: String,
        newDurationMs: Long,
        positionMs: Long,
        playing: Boolean,
        buffering: Boolean,
        rate: Float,
        artworkUrl: String,
    ) {
        val s = session(context)
        var metadataChanged = false
        if (newTitle != title || newArtist != artist || newDurationMs != durationMs) {
            title = newTitle
            artist = newArtist
            durationMs = newDurationMs
            metadataChanged = true
        }
        if (artworkUrl != artUrl) {
            artUrl = artworkUrl
            art = null
            metadataChanged = true
            if (artworkUrl.startsWith("https://")) loadArt(artworkUrl)
        }
        if (metadataChanged) publishMetadata(s)

        val stateCode = when {
            buffering -> PlaybackStateCompat.STATE_BUFFERING
            playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        s.setPlaybackState(state(stateCode, positionMs, if (playing) rate else 0f))
        if (!s.isActive && title.isNotEmpty()) s.isActive = true
    }

    fun stop() {
        val s = session ?: return
        s.setPlaybackState(state(PlaybackStateCompat.STATE_STOPPED, 0L, 0f))
        s.isActive = false
    }

    /** El video actual como elemento de la lista que muestra Android Auto. */
    fun currentDescription(): MediaDescriptionCompat? {
        if (title.isEmpty()) return null
        return MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ID)
            .setTitle(title)
            .setSubtitle(artist)
            .setIconBitmap(art)
            .build()
    }

    private fun state(code: Int, positionMs: Long, speed: Float): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(ACTIONS)
            .setState(code, positionMs.coerceAtLeast(0L), speed, SystemClock.elapsedRealtime())
            .build()

    private fun publishMetadata(s: MediaSessionCompat) {
        val b = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, MEDIA_ID)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
        // Sin duración (por ejemplo, transmisiones en vivo) el auto no muestra la barra de tiempo.
        if (durationMs > 0) b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        art?.let {
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
        }
        s.setMetadata(b.build())
    }

    /** Descarga la miniatura del video para mostrarla como carátula. */
    private fun loadArt(url: String) {
        artLoader.execute {
            val bitmap = try {
                download(url)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar la carátula", e)
                null
            }
            if (bitmap != null) {
                main.post {
                    val s = session
                    if (url == artUrl && s != null) {
                        art = bitmap
                        publishMetadata(s)
                    }
                }
            }
        }
    }

    private fun download(url: String): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            val bytes = conn.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= ART_MAX_PX || bounds.outHeight / (sample * 2) >= ART_MAX_PX) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            conn.disconnect()
        }
    }
}
