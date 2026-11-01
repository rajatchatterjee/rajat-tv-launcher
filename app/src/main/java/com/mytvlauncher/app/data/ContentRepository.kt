package com.mytvlauncher.app.data

import android.content.Context
import com.mytvlauncher.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One show or film, as shown in the Home spotlight and content rows. */
data class ContentItem(
    val id: String,
    val title: String,
    val overview: String,
    val year: String?,
    val rating: Double?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val isTv: Boolean,
    val serviceName: String,
    val packageName: String
) {
    /** "tv" or "movie", and the numeric TMDB id, parsed from [id]. */
    val tmdbKind: String get() = id.substringBefore('-')
    val tmdbId: String get() = id.substringAfter('-')
}

fun ContentItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("overview", overview)
    put("year", year ?: ""); put("rating", rating ?: 0.0)
    put("backdrop", backdropUrl ?: ""); put("poster", posterUrl ?: "")
    put("tv", isTv); put("service", serviceName); put("pkg", packageName)
}

fun contentItemFromJson(c: JSONObject, service: String? = null, pkg: String? = null): ContentItem = ContentItem(
    id = c.getString("id"),
    title = c.getString("title"),
    overview = c.optString("overview"),
    year = c.optString("year").ifBlank { null },
    rating = c.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
    backdropUrl = c.optString("backdrop").ifBlank { null },
    posterUrl = c.optString("poster").ifBlank { null },
    isTv = c.optBoolean("tv"),
    serviceName = service ?: c.optString("service"),
    packageName = pkg ?: c.optString("pkg")
)

data class ContentRow(val title: String, val serviceName: String, val packageName: String, val items: List<ContentItem>)

/** A search result: the title, which of *your* apps stream it, and which other services do. */
data class SearchHit(val item: ContentItem, val onYourApps: List<Pair<String, String>>, val elsewhere: List<String>)

/** Quick filters on Home. [tv] null = films only. */
enum class Mood(val label: String, val movie: String, val tv: String?) {
    FUNNY("Funny", "&with_genres=35", "&with_genres=35"),
    THRILLER("Thriller", "&with_genres=53", "&with_genres=80%7C9648"),
    FAMILY("Family night", "&with_genres=10751", "&with_genres=10751"),
    ACTION("Action", "&with_genres=28", "&with_genres=10759"),
    SCIFI("Sci-fi", "&with_genres=878", "&with_genres=10765"),
    ROMANCE("Romance", "&with_genres=10749", null),
    HINDI("Hindi", "&with_original_language=hi", "&with_original_language=hi"),
    SHORT("Under 2 hours", "&with_runtime.gte=60&with_runtime.lte=120", null)
}

/**
 * Everything that comes from TMDB: "New on your apps" and "Popular on
 * <service>" rows, mood rows, search with where-to-watch, and trailers —
 * always limited to streaming apps installed on the TV where it matters.
 *
 * Why TMDB and not the apps' own preview rows: Android only lets privileged
 * (system) apps read other apps' TvProvider preview programs — see CLAUDE.md.
 * TMDB's watch-provider data (sourced from JustWatch) says what streams on
 * each service in India, with real posters and backdrops.
 *
 * Home rows are cached 6 hours in SharedPreferences so they show instantly
 * and work offline (Coil caches the images on disk).
 */
class ContentRepository(context: Context) {

    class Service(val key: String, val label: String, val packages: List<String>)

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMG = "https://image.tmdb.org/t/p/"
        private const val REGION = "IN"
        private const val REFRESH_MS = 6 * 60 * 60 * 1000L

        /** key = provider name lower-cased with only letters/digits; matched with contains(). */
        val SERVICES = listOf(
            Service("netflix", "Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
            Service("amazonprimevideo", "Prime Video", listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
            Service("hotstar", "JioHotstar", listOf("in.startv.hotstar.dplus.tv", "in.startv.hotstar", "in.startv.hotstaronly")),
            Service("jiocinema", "JioCinema", listOf("com.jio.media.ondemand")),
            Service("sonyliv", "SonyLIV", listOf("com.sonyliv")),
            Service("zee5", "ZEE5", listOf("com.graymatrix.did")),
            Service("appletv", "Apple TV+", listOf("com.apple.atve.androidtv.appletv")),
            Service("sunnxt", "Sun NXT", listOf("com.suntv.sunnxt"))
        )

        fun normalise(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
    }

    private val key = BuildConfig.TMDB_API_KEY.trim()
    val hasKey: Boolean get() = key.isNotEmpty()

    private val prefs = context.getSharedPreferences("content_cache", Context.MODE_PRIVATE)
    private val _rows = MutableStateFlow(readCache())
    val rows: StateFlow<List<ContentRow>> = _rows

    @Volatile private var providerCache: Map<String, List<Int>>? = null
    private val moodCache = mutableMapOf<String, List<ContentItem>>()

    private fun installedServices(installed: Set<String>): List<Pair<Service, String>> =
        SERVICES.mapNotNull { s -> s.packages.firstOrNull { it in installed }?.let { s to it } }

    /** Re-fetch when the cache is old or the set of installed streaming apps changed. */
    suspend fun refreshIfDue(installed: Set<String>) = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext
        val services = installedServices(installed)
        // "v2" = cache layout with per-item service + New row; bump to force a refetch.
        val signature = "v2:" + services.joinToString(",") { it.second }
        val fresh = System.currentTimeMillis() - prefs.getLong("fetched_at", 0) < REFRESH_MS
        if (fresh && signature == prefs.getString("signature", null) && _rows.value.isNotEmpty()) return@withContext
        if (services.isEmpty()) { _rows.value = emptyList(); return@withContext }
        try {
            val ids = providerIds()
            val popular = mutableListOf<ContentRow>()
            val fresh2 = mutableListOf<List<ContentItem>>()
            for ((svc, pkg) in services) {
                val p = ids.idsFor(svc)
                if (p.isEmpty()) continue
                val items = mixed(p, svc, pkg, movieExtra = "", tvExtra = "")
                if (items.isNotEmpty()) popular += ContentRow("Popular on ${svc.label}", svc.label, pkg, items)
                val from = daysAgo(45)
                val today = daysAgo(0)
                fresh2 += mixed(
                    p, svc, pkg,
                    movieExtra = "&primary_release_date.gte=$from&primary_release_date.lte=$today",
                    tvExtra = "&air_date.gte=${daysAgo(10)}&air_date.lte=$today",
                    minVotes = 2
                ).take(6)
            }
            val newRow = roundRobin(fresh2).distinctBy { it.id }.take(16)
            val rows = buildList {
                if (newRow.isNotEmpty()) add(ContentRow("New on your apps", "", "", newRow))
                addAll(popular)
            }
            if (rows.isNotEmpty()) {
                _rows.value = rows
                writeCache(rows, signature)
            }
        } catch (e: Exception) {
            // Keep whatever is cached; try again next resume.
        }
    }

    /** A row for one Home mood chip, across all installed services. Cached for the session. */
    suspend fun mood(mood: Mood, installed: Set<String>): List<ContentItem> = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext emptyList()
        val services = installedServices(installed)
        val cacheKey = mood.name + services.joinToString { it.second }
        moodCache[cacheKey]?.let { return@withContext it }
        try {
            val ids = providerIds()
            val perService = coroutineScope {
                services.map { (svc, pkg) ->
                    async {
                        val p = ids.idsFor(svc)
                        if (p.isEmpty()) emptyList()
                        else runCatching { mixed(p, svc, pkg, mood.movie, mood.tv) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            roundRobin(perService).distinctBy { it.id }.take(20).also { moodCache[cacheKey] = it }
        } catch (e: Exception) { emptyList() }
    }

    /** Title search with where-to-watch in India, ordered so things on your apps come first. */
    suspend fun search(query: String, installed: Set<String>): List<SearchHit> = withContext(Dispatchers.IO) {
        if (!hasKey || query.isBlank()) return@withContext emptyList()
        val services = installedServices(installed)
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val results = get("/search/multi?query=$q&include_adult=false&language=en-US&region=$REGION&page=1")
            .optJSONArray("results") ?: return@withContext emptyList()
        val items = (0 until results.length()).mapNotNull { i ->
            val r = results.getJSONObject(i)
            when (r.optString("media_type")) {
                "tv" -> parseItem(r, "tv")
                "movie" -> parseItem(r, "movie")
                else -> null
            }
        }.take(12)
        val hits = coroutineScope {
            items.map { item ->
                async {
                    val names = runCatching { streamingOn(item) }.getOrDefault(emptyList())
                    val yours = names.mapNotNull { n ->
                        services.firstOrNull { (svc, _) -> normalise(n).contains(svc.key) }?.let { it.first.label to it.second }
                    }.distinct()
                    val elsewhere = names.filter { n -> services.none { (svc, _) -> normalise(n).contains(svc.key) } }.distinct()
                    val first = yours.firstOrNull()
                    SearchHit(item.copy(serviceName = first?.first ?: "", packageName = first?.second ?: ""), yours, elsewhere)
                }
            }.awaitAll()
        }
        hits.sortedByDescending { it.onYourApps.isNotEmpty() }
    }

    /** YouTube video key of the title's trailer (or teaser), if TMDB has one. */
    suspend fun trailerKey(item: ContentItem): String? = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext null
        try {
            val res = get("/${item.tmdbKind}/${item.tmdbId}/videos?language=en-US").optJSONArray("results")
                ?: return@withContext null
            val vids = (0 until res.length()).map { res.getJSONObject(it) }.filter { it.optString("site") == "YouTube" }
            (vids.firstOrNull { it.optString("type") == "Trailer" && it.optBoolean("official") }
                ?: vids.firstOrNull { it.optString("type") == "Trailer" }
                ?: vids.firstOrNull { it.optString("type") == "Teaser" }
                ?: vids.firstOrNull())?.optString("key")
        } catch (e: Exception) { null }
    }

    // ---- TMDB calls ----

    private fun get(path: String): JSONObject {
        val sep = if (path.contains('?')) '&' else '?'
        // Short keys are v3 "API keys" (query param); long ones are v4 read tokens (Bearer).
        val useBearer = key.length > 40
        val url = URL(BASE + path + if (useBearer) "" else "${sep}api_key=$key")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            if (useBearer) setRequestProperty("Authorization", "Bearer $key")
        }
        return conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    /** normalised provider name -> provider ids, across TV and movie lists. */
    private fun providerIds(): Map<String, List<Int>> {
        providerCache?.let { return it }
        val out = mutableMapOf<String, MutableList<Int>>()
        for (kind in listOf("tv", "movie")) {
            val results = get("/watch/providers/$kind?watch_region=$REGION").optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val p = results.getJSONObject(i)
                out.getOrPut(normalise(p.optString("provider_name"))) { mutableListOf() } += p.getInt("provider_id")
            }
        }
        return out.also { providerCache = it }
    }

    private fun Map<String, List<Int>>.idsFor(svc: Service): List<Int> =
        filterKeys { it.contains(svc.key) }.values.flatten().distinct()

    /** Service names streaming (flatrate) this title in India. */
    private fun streamingOn(item: ContentItem): List<String> {
        val inRegion = get("/${item.tmdbKind}/${item.tmdbId}/watch/providers")
            .optJSONObject("results")?.optJSONObject(REGION) ?: return emptyList()
        val flat = inRegion.optJSONArray("flatrate") ?: return emptyList()
        return (0 until flat.length()).map { flat.getJSONObject(it).optString("provider_name") }
    }

    /** TV and films for one service, interleaved, with backdrops only. */
    private fun mixed(ids: List<Int>, svc: Service, pkg: String, movieExtra: String, tvExtra: String?, minVotes: Int = 15): List<ContentItem> {
        val tv = if (tvExtra == null) emptyList() else runCatching { discover("tv", ids, tvExtra, minVotes) }.getOrDefault(emptyList())
        val movies = runCatching { discover("movie", ids, movieExtra, minVotes) }.getOrDefault(emptyList())
        return roundRobin(listOf(tv, movies))
            .filter { it.backdropUrl != null }
            .distinctBy { it.id }
            .take(16)
            .map { it.copy(serviceName = svc.label, packageName = pkg) }
    }

    private fun discover(kind: String, providerIds: List<Int>, extra: String, minVotes: Int): List<ContentItem> {
        val providers = providerIds.joinToString("%7C") // "|" = any of these
        val json = get(
            "/discover/$kind?watch_region=$REGION&with_watch_providers=$providers" +
                "&with_watch_monetization_types=flatrate&sort_by=popularity.desc" +
                "&vote_count.gte=$minVotes&include_adult=false&language=en-US&page=1" + extra
        )
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i -> parseItem(results.getJSONObject(i), kind) }
    }

    private fun parseItem(r: JSONObject, kind: String): ContentItem {
        val isTv = kind == "tv"
        return ContentItem(
            id = "$kind-${r.optInt("id")}",
            title = r.optString(if (isTv) "name" else "title"),
            overview = r.optString("overview"),
            year = r.optString(if (isTv) "first_air_date" else "release_date").take(4).ifBlank { null },
            rating = r.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            backdropUrl = r.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { IMG + "w1280" + it },
            posterUrl = r.optString("poster_path").takeIf { it.startsWith("/") }?.let { IMG + "w342" + it },
            isTv = isTv,
            serviceName = "",
            packageName = ""
        )
    }

    private fun <T> roundRobin(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }

    private fun daysAgo(days: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - days * 86_400_000L))

    // ---- Cache ----

    private fun writeCache(rows: List<ContentRow>, signature: String) {
        val arr = JSONArray()
        rows.forEach { row ->
            val items = JSONArray()
            row.items.forEach { items.put(it.toJson()) }
            arr.put(JSONObject().apply {
                put("title", row.title); put("service", row.serviceName); put("pkg", row.packageName); put("items", items)
            })
        }
        prefs.edit()
            .putString("rows", arr.toString())
            .putString("signature", signature)
            .putLong("fetched_at", System.currentTimeMillis())
            .apply()
    }

    private fun readCache(): List<ContentRow> = try {
        val arr = JSONArray(prefs.getString("rows", "[]"))
        (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            val service = r.getString("service")
            val pkg = r.getString("pkg")
            val items = r.getJSONArray("items")
            ContentRow(r.getString("title"), service, pkg, (0 until items.length()).map { j ->
                val c = items.getJSONObject(j)
                // Older caches had no per-item service; fall back to the row's.
                contentItemFromJson(c, c.optString("service").ifBlank { service }, c.optString("pkg").ifBlank { pkg })
            })
        }
    } catch (e: Exception) { emptyList() }
}
