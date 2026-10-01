package uy.autovideo.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Servicio de música que Android Auto usa para conectarse a la sesión de reproducción:
 * así el tablero del auto muestra lo que suena y funcionan los botones del volante.
 */
class NowPlayingService : MediaBrowserServiceCompat() {

    override fun onCreate() {
        super.onCreate()
        sessionToken = NowPlaying.session(this).sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId == ROOT_ID) {
            NowPlaying.currentDescription()?.let {
                items.add(MediaBrowserCompat.MediaItem(it, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
        }
        result.sendResult(items)
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
