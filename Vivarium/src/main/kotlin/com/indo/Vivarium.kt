package com.indo

import android.util.Log
import android.webkit.CookieManager
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * vivarium.st. The catalog (home, search, details, seasons and episodes) comes from TMDB through
 * CloudStream's TmdbProvider; the site itself is only used to play.
 *
 * The player's default "Vivarium" source streams through an encrypted relay into a blob: URL that
 * nothing outside the page can play. Its other sources (Hermes, Hemera, ...) load a plain HLS master
 * from <sub>.itsmeowenthankyouforyourservice.online/api/cs/<id>/master.m3u8. So open the watch page
 * in a WebView, switch the player to one of those sources through its own Settings > Source menu,
 * and take the master playlist it requests. Nothing is decrypted here.
 */
class Vivarium : TmdbProvider() {
    override var name = "Vivarium"
    override var mainUrl = "https://vivarium.st"
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
        // Movies: /m/<tmdb>?w=1 (the site adds the slug itself). Episodes: /s/<slug>-<tmdb>?w=1&s=&e=,
        // where any slug works ("j" is what the site's own continue-watching links use).
        val pageUrl = if (link.season != null && link.episode != null) {
            "$mainUrl/s/j-$id?w=1&s=${link.season}&e=${link.episode}"
        } else {
            "$mainUrl/m/$id?w=1"
        }

        var lastState = ""
        val request = try {
            Log.d(TAG, "webview start: $pageUrl")
            WebViewResolver(
                interceptUrl = MASTER_URL,
                additionalUrls = listOf(TRACE_URL),
                userAgent = null,
                useOkhttp = false,
                script = PICK_SOURCE_JS,
                scriptCallback = { state ->
                    if (state != lastState) {
                        lastState = state
                        Log.d(TAG, "page state: $state")
                    }
                },
                timeout = 90_000L
            ).resolveUsingWebView(
                url = pageUrl,
                referer = "$mainUrl/",
                headers = emptyMap(),
                method = "GET"
            ) { req ->
                Log.d(TAG, "webview request: ${req.method} ${req.url.host}${req.url.encodedPath.take(40)}")
                false
            }.first
        } catch (e: Exception) {
            Log.d(TAG, "webview failed: $e")
            null
        }
        if (request == null) {
            Log.d(TAG, "no master playlist, last page state: $lastState")
            return false
        }

        val streamUrl = request.url.toString()
        Log.d(TAG, "stream: ${request.url.host}")
        val linkHeaders = mutableMapOf("Origin" to mainUrl)
        (request.header("User-Agent") ?: WebViewResolver.webViewUserAgent)?.let { linkHeaders["User-Agent"] = it }
        runCatching { CookieManager.getInstance().getCookie(streamUrl) }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { linkHeaders["Cookie"] = it }
        callback(newExtractorLink(name, "$name HLS", streamUrl, type = ExtractorLinkType.M3U8) {
            this.referer = "$mainUrl/"
            this.headers = linkHeaders
            this.quality = Qualities.Unknown.value
        })
        return true
    }

    companion object {
        private const val TAG = "Vivarium"

        private val MASTER_URL = Regex("""/api/cs/[^/?#]+/master\.m3u8""")

        // Requests logged while the WebView runs, to see how far the page got.
        private val TRACE_URL = Regex("""vivarium\.st/api/|itsmeowenthankyouforyourservice\.online/api/|challenges\.cloudflare\.com/""")

        // Once the player is up, opens Settings > Source and picks the first HLS source; if no
        // playlist was requested 9 s later, tries the next one. Returns a short state string; when it
        // changes, a .png that the resolver answers locally (no network) makes the resolver run this
        // script again so the state reaches the log.
        private const val PICK_SOURCE_JS = """
            (function () {
                var ORDER = ['Hermes', 'Hemera', 'Nyx', 'Tyche', 'Eos', 'Theia', 'Hyperion', 'Orion', 'Hades'];
                function find(re) {
                    var all = document.querySelectorAll('button, [role=button], [role=menuitem], [role=option], [role=radio], li, label');
                    for (var i = 0; i < all.length; i++) {
                        var t = (all[i].getAttribute('aria-label') || all[i].innerText || '').trim();
                        if (re.test(t)) return all[i];
                    }
                    return null;
                }
                function tick() {
                    if (!document.querySelector('video')) return;
                    var now = Date.now();
                    if (window.__csVivAt && now - window.__csVivAt < 9000) return;
                    var idx = window.__csVivIdx || 0;
                    if (idx >= ORDER.length) return;
                    var settings = find(/^Settings$/);
                    if (!settings) return;
                    window.__csVivAt = now;
                    settings.click();
                    setTimeout(function () {
                        var source = find(/^Source/);
                        if (!source) { window.__csVivNote = 'no source item'; return; }
                        source.click();
                        setTimeout(function () {
                            var opt = find(new RegExp('^' + ORDER[idx] + '$'));
                            window.__csVivIdx = idx + 1;
                            window.__csVivNote = opt ? 'picked ' + ORDER[idx] : 'no ' + ORDER[idx];
                            if (opt) opt.click();
                        }, 800);
                    }, 800);
                }
                function state() {
                    return 'ready=' + document.readyState + ' videos=' + document.querySelectorAll('video').length
                        + ' settings=' + !!find(/^Settings$/) + ' ' + (window.__csVivNote || 'waiting');
                }
                if (!window.__csViv) {
                    window.__csViv = true;
                    var last = '';
                    setInterval(function () {
                        tick();
                        var s = state();
                        if (s !== last) { last = s; new Image().src = location.origin + '/__cs_state.png?' + Date.now(); }
                    }, 1500);
                }
                return state();
            })();
        """
    }
}
