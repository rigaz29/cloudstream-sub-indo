package com.indo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.JsonAsString
import java.net.URLEncoder

/**
 * MovieBox (officialmoviebox.com; themoviebox.org redirects there).
 *
 * The site reads its catalog from h5-api.aoneroom.com with a guest token that the API hands out in
 * the x-user header of its home call, and gets the streams from <site>/wefeed-h5api-bff/subject/play
 * with the watch page as Referer. This plugin makes the same calls with the same headers: MP4 (H.264)
 * links per resolution and a DASH (HEVC) link that needs the play answer's signCookie as X-MB-Token,
 * plus the subtitles from the caption call.
 */
class MovieBox : MainAPI() {
    override var mainUrl = "https://officialmoviebox.com"
    private val apiBase = "https://h5-api.aoneroom.com"

    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private var guestToken: String? = null

    override val mainPage = mainPageOf(
        "872031290915189720" to "Trending",
        "4380734070238626200" to "K-Drama: New Release",
        "6528093688173053896" to "Trending Indonesia"
    )

    private val baseHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to USER_AGENT,
        "X-Client-Info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "X-Request-Lang" to "en"
    )

    /** The guest token the API gives every visitor (x-user header of the home call). */
    private suspend fun token(refresh: Boolean = false): String {
        if (!refresh) guestToken?.let { return it }
        val host = mainUrl.substringAfter("://").substringBefore("/")
        val resp = app.get("$apiBase/wefeed-h5api-bff/home?host=$host", headers = baseHeaders, referer = "$mainUrl/")
        val token = tryParseJson<Map<String, Any?>>(resp.headers["x-user"] ?: "")?.get("token")?.toString()
        if (!token.isNullOrBlank()) guestToken = token
        else Log.d(TAG, "no guest token in the home answer")
        return guestToken.orEmpty()
    }

    private fun authHeaders(token: String) = baseHeaders + mapOf("Authorization" to "Bearer $token")

    private fun isOk(raw: String?) = tryParseJson<Map<String, Any?>>(raw ?: "")?.get("code")?.toString()?.let { it == "0" || it == "0.0" } == true

    /** GET on the catalog API with the guest token; asks for a new token once if the answer is not ok. */
    private suspend fun apiGet(path: String): String {
        var raw = app.get("$apiBase$path", headers = authHeaders(token()), referer = "$mainUrl/").text
        if (!isOk(raw)) {
            raw = app.get("$apiBase$path", headers = authHeaders(token(refresh = true)), referer = "$mainUrl/").text
        }
        return raw
    }

    private suspend fun apiPost(path: String, body: String): String {
        val headers = authHeaders(token()) + mapOf("Content-Type" to "application/json")
        var raw = app.post("$apiBase$path", json = JsonAsString(body), headers = headers, referer = "$mainUrl/").text
        if (!isOk(raw)) {
            val retry = authHeaders(token(refresh = true)) + mapOf("Content-Type" to "application/json")
            raw = app.post("$apiBase$path", json = JsonAsString(body), headers = retry, referer = "$mainUrl/").text
        }
        return raw
    }

    private fun detailPathFromUrl(url: String): String {
        return url.substringBefore("?").substringAfterLast("/")
    }

    private fun toTvType(subjectType: Int?): TvType = when (subjectType) {
        2 -> TvType.Anime
        3 -> TvType.TvSeries
        else -> TvType.Movie
    }

    private fun toInt(v: Any?): Int? = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Float -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

    private fun toSubjectList(raw: String): List<Map<String, Any?>> {
        val root = tryParseJson<Map<String, Any?>>(raw) ?: return emptyList()
        val data = root["data"] as? Map<*, *> ?: return emptyList()
        val list = data["subjectList"] as? List<*> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return list.mapNotNull { it as? Map<String, Any?> }
    }

    private fun toSearchResponseFromSubject(s: Map<String, Any?>): SearchResponse? {
        val path = s["detailPath"]?.toString()?.ifBlank { null } ?: return null
        val title = s["title"]?.toString()?.ifBlank { null } ?: return null
        val subjectType = toInt(s["subjectType"])
        val tvType = toTvType(subjectType)
        val cover = (s["cover"] as? Map<*, *>)?.get("url")?.toString()
        val imdbRating = s["imdbRatingValue"]?.toString()?.toDoubleOrNull()

        return when (tvType) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, "$mainUrl/moviesDetail/$path", TvType.TvSeries) {
                this.posterUrl = cover
                this.score = Score.from10(imdbRating)
            }
            TvType.Anime -> newAnimeSearchResponse(title, "$mainUrl/moviesDetail/$path", TvType.Anime) {
                this.posterUrl = cover
                this.score = Score.from10(imdbRating)
            }
            else -> newMovieSearchResponse(title, "$mainUrl/moviesDetail/$path", TvType.Movie) {
                this.posterUrl = cover
                this.score = Score.from10(imdbRating)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val raw = apiGet("/wefeed-h5api-bff/ranking-list/content?id=${request.data}&page=$page&perPage=12")
        val items = toSubjectList(raw)
            .mapNotNull { toSearchResponseFromSubject(it) }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allItems = mutableListOf<Map<String, Any?>>()
        var page = 1
        var hasMore = true
        while (page <= MAX_SEARCH_PAGES && hasMore) {
            val keyword = query.replace("\\", "\\\\").replace("\"", "\\\"")
            val raw = apiPost("/wefeed-h5api-bff/subject/search", """{"keyword":"$keyword","page":$page,"perPage":28,"subjectType":0}""")
            val data = tryParseJson<Map<String, Any?>>(raw)?.get("data") as? Map<*, *> ?: break
            hasMore = (data["pager"] as? Map<*, *>)?.get("hasMore") as? Boolean ?: false
            val items = data["items"] as? List<*> ?: emptyList<Any>()
            if (items.isEmpty()) break
            @Suppress("UNCHECKED_CAST")
            allItems.addAll(items.mapNotNull { it as? Map<String, Any?> })
            page++
        }
        return allItems
            .distinctBy { it["detailPath"]?.toString() }
            .filter { it["detailPath"]?.toString()?.isNotBlank() == true }
            .mapNotNull { toSearchResponseFromSubject(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val detailPath = detailPathFromUrl(url)
        val raw = apiGet("/wefeed-h5api-bff/detail?detailPath=$detailPath")

        val root = tryParseJson<Map<String, Any?>>(raw) ?: throw ErrorLoadingException("Invalid detail response")
        val data = root["data"] as? Map<*, *> ?: throw ErrorLoadingException("Missing data")
        val subject = data["subject"] as? Map<*, *> ?: throw ErrorLoadingException("Missing subject")
        val resource = data["resource"] as? Map<*, *>

        val title = subject["title"]?.toString() ?: throw ErrorLoadingException("Title not found")
        val subjectId = subject["subjectId"]?.toString().orEmpty()
        val tvType = toTvType(toInt(subject["subjectType"]))
        val imdbRating = subject["imdbRatingValue"]?.toString()?.toDoubleOrNull()
        val plot = subject["description"]?.toString()
        val poster = (subject["cover"] as? Map<*, *>)?.get("url")?.toString()
        val tags = subject["genre"]?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val year = subject["releaseDate"]?.toString()?.take(4)?.toIntOrNull()
        val pageUrl = "$mainUrl/moviesDetail/$detailPath"

        val seasons = (resource?.get("seasons") as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()

        val isSeriesLike = seasons.isNotEmpty() && (toInt(seasons.first()["maxEp"]) ?: 0) > 1

        if (isSeriesLike) {
            val episodes = mutableListOf<Episode>()
            seasons.forEach { s ->
                val seasonNo = toInt(s["se"]) ?: return@forEach
                val allEp = s["allEp"]?.toString()
                val eps = if (!allEp.isNullOrBlank()) {
                    allEp.split(',').mapNotNull { it.trim().toIntOrNull() }
                } else {
                    (1..(toInt(s["maxEp"]) ?: 0)).toList()
                }
                eps.forEach { ep ->
                    episodes.add(newEpisode("$pageUrl?sid=$subjectId&se=$seasonNo&ep=$ep") {
                        this.season = seasonNo
                        this.episode = ep
                        this.name = "Episode $ep"
                    })
                }
            }

            return newTvSeriesLoadResponse(title, pageUrl, tvType, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.score = Score.from10(imdbRating)
            }
        }

        val firstSeason = seasons.firstOrNull()
        val defaultSe = toInt(firstSeason?.get("se")) ?: 0
        val defaultEp = if ((toInt(firstSeason?.get("maxEp")) ?: 0) >= 1) 1 else 0

        return newMovieLoadResponse(title, pageUrl, tvType, "$pageUrl?sid=$subjectId&se=$defaultSe&ep=$defaultEp") {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
            this.year = year
            this.score = Score.from10(imdbRating)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val detailPath = detailPathFromUrl(data)
        val sid = Regex("[?&](?:sid|id)=([^&]+)").find(data)?.groupValues?.getOrNull(1)
        var se = Regex("[?&](?:se|detailSe)=(\\d+)").find(data)?.groupValues?.getOrNull(1) ?: "0"
        var ep = Regex("[?&](?:ep|detailEp)=(\\d+)").find(data)?.groupValues?.getOrNull(1) ?: "0"

        val detData = tryParseJson<Map<String, Any?>>(apiGet("/wefeed-h5api-bff/detail?detailPath=$detailPath"))?.get("data") as? Map<*, *>
        val detSubject = detData?.get("subject") as? Map<*, *>
        val detResource = detData?.get("resource") as? Map<*, *>

        val subjectId = sid?.ifBlank { null } ?: detSubject?.get("subjectId")?.toString().orEmpty()
        if (subjectId.isBlank()) return false

        if (se == "0" && ep == "0") {
            val firstS = (detResource?.get("seasons") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull()
            if (firstS != null && (toInt(firstS["se"]) ?: 0) >= 1) {
                se = (toInt(firstS["se"]) ?: 1).toString()
                ep = if ((toInt(firstS["maxEp"]) ?: 0) >= 1) "1" else "0"
            }
        }

        // Dubs are separate subjects (audio or subtitle versions); try the original and the
        // Indonesian and English ones.
        val dubs = (detSubject?.get("dubs") as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
            .filter { d ->
                val code = d["lanCode"]?.toString()?.lowercase().orEmpty()
                d["original"] == true || code.startsWith("id") || code.startsWith("in") || code.startsWith("en")
            }
            .ifEmpty { listOf(mapOf<String, Any?>("subjectId" to subjectId, "lanName" to "Original")) }

        var found = 0
        val subtitleUrls = HashSet<String>()
        for (dub in dubs) {
            val dubId = dub["subjectId"]?.toString() ?: continue
            val dubName = dub["lanName"]?.toString()?.takeIf { dubs.size > 1 }
            val dubPath = dub["detailPath"]?.toString()?.ifBlank { null } ?: detailPath

            for ((curSe, curEp) in listOf(se to ep) + (if (se == "0" && ep == "0") listOf("1" to "1") else listOf("0" to "0"))) {
                val watchPage = "$mainUrl/movies/$dubPath?id=$dubId&type=/movie/detail&detailSe=$curSe&detailEp=$curEp&lang=en"
                val playRaw = app.get(
                    "$mainUrl/wefeed-h5api-bff/subject/play?subjectId=$dubId&se=$curSe&ep=$curEp&detailPath=$dubPath&streamSignType=1",
                    headers = baseHeaders,
                    referer = watchPage
                ).text
                val play = tryParseJson<Map<String, Any?>>(playRaw)?.get("data") as? Map<*, *> ?: continue
                if (play["hasResource"] as? Boolean != true) continue
                if (play["vipLocked"] == true || play["limited"] == true) {
                    // VIP-only or limited on the site itself: leave it to the site.
                    Log.d(TAG, "${dub["lanName"] ?: "original"} se=$curSe ep=$curEp is locked (vip=${play["vipLocked"]}, limited=${play["limited"]})")
                    continue
                }

                val mp4 = (play["streams"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                val dash = (play["dash"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                val hls = (play["hls"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                if (mp4.isEmpty() && dash.isEmpty() && hls.isEmpty()) continue

                suspend fun emit(item: Map<*, *>, kind: String, type: ExtractorLinkType?) {
                    val url = item["url"]?.toString()?.takeIf { it.startsWith("http") } ?: return
                    val heights = item["resolutions"]?.toString().orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
                    val height = heights.maxOrNull()
                    val codec = item["codecName"]?.toString()?.uppercase()?.ifBlank { null }
                    val label = listOfNotNull(dubName, kind, codec, height?.let { "${it}p" }).joinToString(" ")
                    val headers = mutableMapOf("User-Agent" to USER_AGENT)
                    item["signCookie"]?.toString()?.takeIf { it.isNotBlank() }?.let { headers["X-MB-Token"] = it }
                    found++
                    callback(newExtractorLink(name, "$name $label", url, type = type) {
                        this.referer = watchPage
                        this.headers = headers
                        this.quality = height ?: Qualities.Unknown.value
                    })
                }
                mp4.forEach { emit(it, "MP4", ExtractorLinkType.VIDEO) }
                dash.forEach { emit(it, "DASH", ExtractorLinkType.DASH) }
                hls.forEach { emit(it, "HLS", ExtractorLinkType.M3U8) }

                // Subtitles of the first stream that has an id (the same files for every quality), from
                // the first version that has any: the dubs carry the same languages again.
                val captionSource = (dash.map { "DASH" to it } + mp4.map { "MP4" to it }).firstOrNull { it.second["id"] != null }
                if (captionSource != null && subtitleUrls.isEmpty()) {
                    val (format, item) = captionSource
                    val capRaw = apiGet("/wefeed-h5api-bff/subject/caption?format=$format&id=${item["id"]}&subjectId=$dubId&detailPath=$dubPath")
                    val captions = ((tryParseJson<Map<String, Any?>>(capRaw)?.get("data") as? Map<*, *>)?.get("captions") as? List<*>)
                        ?.mapNotNull { it as? Map<*, *> }.orEmpty()
                    captions.forEach { cap ->
                        val capUrl = cap["url"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                        if (!subtitleUrls.add(capUrl)) return@forEach
                        subtitleCallback(newSubtitleFile(languageName(cap["lan"]?.toString(), cap["lanName"]?.toString()), capUrl))
                    }
                }
                Log.d(TAG, "${dub["lanName"] ?: "original"} se=$curSe ep=$curEp: ${mp4.size} mp4, ${dash.size} dash, ${hls.size} hls")
                break
            }
        }
        Log.d(TAG, "links: $found, subtitles: ${subtitleUrls.size}")
        return found > 0
    }

    /** "in_id" -> Indonesian, "en" -> English, ...; falls back to the site's own (native) name. */
    private fun languageName(code: String?, siteName: String?): String {
        val c = code?.lowercase().orEmpty()
        return SubtitleHelper.fromTagToEnglishLanguageName(c)
            ?: SubtitleHelper.fromTagToEnglishLanguageName(c.substringBefore('_'))
            ?: siteName?.ifBlank { null }
            ?: code ?: "Unknown"
    }

    companion object {
        private const val TAG = "MovieBox"
        private const val MAX_SEARCH_PAGES = 10
    }
}
