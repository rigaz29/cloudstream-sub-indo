package com.indo

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * arrowtv.net. The catalog (home, search, details, seasons and episodes) comes from TMDB through
 * CloudStream's TmdbProvider; ArrowTV itself uses TMDB ids.
 *
 * ArrowTV's watch page (/watch/movie/<tmdb>, /watch/tv/<tmdb>?season=&episode=) embeds the Cinezo
 * player (player.cinezo.live/embed/...), which looks up its servers on its own and loads an HLS
 * master (media.punkflix.net/s/<hash>/index.m3u8 on the first server) without any click. Open the
 * watch page in a WebView and take the first .m3u8 request; the player's own requests to its
 * source endpoints are left to the page.
 */
class ArrowTV(private val appContext: Context) : TmdbProvider() {
    override var name = "ArrowTV"
    override var mainUrl = "https://arrowtv.net"
    override var lang = "en"
    override val hasMainPage = true
    override val useMetaLoadResponse = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = tryParseJson<TmdbLink>(data) ?: return false
        val id = link.tmdbID ?: return false
        val pageUrl = if (link.season != null && link.episode != null) {
            "$mainUrl/watch/tv/$id?season=${link.season}&episode=${link.episode}"
        } else {
            "$mainUrl/watch/movie/$id"
        }

        val caught = try {
            catchPlaylist(pageUrl)
        } catch (e: Exception) {
            Log.d(TAG, "webview failed: $e")
            null
        } ?: return false

        fun header(name: String) = caught.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val linkHeaders = mutableMapOf<String, String>()
        header("User-Agent")?.let { linkHeaders["User-Agent"] = it }
        linkHeaders["Origin"] = header("Origin") ?: PLAYER_ORIGIN
        callback(newExtractorLink(name, "$name HLS", caught.url, type = ExtractorLinkType.M3U8) {
            this.referer = header("Referer") ?: "$PLAYER_ORIGIN/"
            this.headers = linkHeaders
            this.quality = Qualities.Unknown.value
        })
        return true
    }

    private class Caught(val url: String, val headers: Map<String, String>)

    /**
     * Opens [pageUrl] in a WebView of our own and waits for the first HLS playlist request (the
     * player's master). CloudStream's WebViewResolver is not used: it answers .css requests with an
     * empty body, which breaks sites that wait for their stylesheets; here every request is left
     * alone. The WebView is built with the plugin's context (com.lagradost.api.getContext() is not
     * available to plugins).
     */
    private suspend fun catchPlaylist(pageUrl: String): Caught? {
        val found = CompletableDeferred<Caught>()
        var webView: WebView? = null
        Log.d(TAG, "webview start: $pageUrl")
        withContext(Dispatchers.Main) {
            webView = WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (TRACE_URL.containsMatchIn(url)) {
                            Log.d(TAG, "webview request: ${request.method} ${request.url.host}${(request.url.path ?: "").take(40)}")
                        }
                        if (PLAYLIST_URL.containsMatchIn(url) && !found.isCompleted) {
                            found.complete(Caught(url, request.requestHeaders ?: emptyMap()))
                        }
                        return null
                    }
                }
                loadUrl(pageUrl, mapOf("Referer" to "$mainUrl/"))
            }
        }
        val caught = withTimeoutOrNull(CATCH_TIMEOUT_MS) { found.await() }
        withContext(Dispatchers.Main) {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
        if (caught == null) Log.d(TAG, "no playlist within ${CATCH_TIMEOUT_MS / 1000} s")
        else Log.d(TAG, "stream: ${caught.url.substringAfter("://").substringBefore("/")}")
        return caught
    }

    companion object {
        private const val TAG = "ArrowTV"
        private const val CATCH_TIMEOUT_MS = 45_000L
        private const val PLAYER_ORIGIN = "https://player.cinezo.live"

        private val PLAYLIST_URL = Regex("""\.m3u8(\?|$)""")

        // Requests logged while the WebView runs: the player and its source lookups.
        private val TRACE_URL = Regex("""player\.cinezo\.live/embed|punkflix\.net/|yenime\.net/|flikhub\.net/""")
    }
}
