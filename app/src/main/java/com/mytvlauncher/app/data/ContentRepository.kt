package com.mytvlauncher.app.data

import android.content.Context
import com.mytvlauncher.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
)

data class ContentRow(val title: String, val serviceName: String, val packageName: String, val items: List<ContentItem>)

/**
 * "Popular on Netflix / Prime Video / JioHotstar…" rows for streaming apps
 * that are installed on the TV.
 *
 * Why TMDB and not the apps' own preview rows: Android only lets privileged
 * (system) apps read other apps' TvProvider preview programs — see CLAUDE.md.
 * TMDB's watch-provider data (sourced from JustWatch) says what's streaming
 * on each service in India, with real posters and backdrops.
 *
 * Results are cached in SharedPreferences for 6 hours so Home shows posters
 * instantly and still works offline (Coil caches the images on disk).
 */
class ContentRepository(context: Context) {

    private class Service(val key: String, val label: String, val packages: List<String>)

    private companion object {
        const val BASE = "https://api.themoviedb.org/3"
        const val IMG = "https://image.tmdb.org/t/p/"
        const val REGION = "IN"
        const val REFRESH_MS = 6 * 60 * 60 * 1000L

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
    }

    private val key = BuildConfig.TMDB_API_KEY.trim()
    val hasKey: Boolean get() = key.isNotEmpty()

    private val prefs = context.getSharedPreferences("content_cache", Context.MODE_PRIVATE)
    private val _rows = MutableStateFlow(readCache())
    val rows: StateFlow<List<ContentRow>> = _rows

    /** Re-fetch when the cache is old or the set of installed streaming apps changed. */
    suspend fun refreshIfDue(installed: Set<String>) = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext
        val services = SERVICES.mapNotNull { s -> s.packages.firstOrNull { it in installed }?.let { s to it } }
        val signature = services.joinToString(",") { it.second }
        val fresh = System.currentTimeMillis() - prefs.getLong("fetched_at", 0) < REFRESH_MS
        if (fresh && signature == prefs.getString("signature", null) && _rows.value.isNotEmpty()) return@withContext
        if (services.isEmpty()) { _rows.value = emptyList(); return@withContext }
        try {
            val providerIds = providerIds()
            val rows = services.mapNotNull { (svc, pkg) ->
                val ids = providerIds.filterKeys { it.contains(svc.key) }.values.flatten().distinct()
                if (ids.isEmpty()) return@mapNotNull null
                val tv = runCatching { discover("tv", ids) }.getOrDefault(emptyList())
                val movies = runCatching { discover("movie", ids) }.getOrDefault(emptyList())
                val items = interleave(tv, movies)
                    .filter { it.backdropUrl != null }
                    .distinctBy { it.id }
                    .take(16)
                    .map { it.copy(serviceName = svc.label, packageName = pkg) }
                if (items.isEmpty()) null else ContentRow("Popular on ${svc.label}", svc.label, pkg, items)
            }
            if (rows.isNotEmpty()) {
                _rows.value = rows
                writeCache(rows, signature)
            }
        } catch (e: Exception) {
            // Keep whatever is cached; try again next resume.
        }
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
        val out = mutableMapOf<String, MutableList<Int>>()
        for (kind in listOf("tv", "movie")) {
            val results = get("/watch/providers/$kind?watch_region=$REGION").optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val p = results.getJSONObject(i)
                val name = p.optString("provider_name").lowercase().filter { it.isLetterOrDigit() }
                out.getOrPut(name) { mutableListOf() } += p.getInt("provider_id")
            }
        }
        return out
    }

    private fun discover(kind: String, providerIds: List<Int>): List<ContentItem> {
        val providers = providerIds.joinToString("%7C") // "|" = any of these
        val json = get(
            "/discover/$kind?watch_region=$REGION&with_watch_providers=$providers" +
                "&with_watch_monetization_types=flatrate&sort_by=popularity.desc" +
                "&vote_count.gte=15&include_adult=false&language=en-US&page=1"
        )
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val isTv = kind == "tv"
            ContentItem(
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
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(out::add)
            b.getOrNull(i)?.let(out::add)
        }
        return out
    }

    // ---- Cache ----

    private fun writeCache(rows: List<ContentRow>, signature: String) {
        val arr = JSONArray()
        rows.forEach { row ->
            val items = JSONArray()
            row.items.forEach { c ->
                items.put(JSONObject().apply {
                    put("id", c.id); put("title", c.title); put("overview", c.overview)
                    put("year", c.year ?: ""); put("rating", c.rating ?: 0.0)
                    put("backdrop", c.backdropUrl ?: ""); put("poster", c.posterUrl ?: "")
                    put("tv", c.isTv)
                })
            }
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
                ContentItem(
                    id = c.getString("id"),
                    title = c.getString("title"),
                    overview = c.optString("overview"),
                    year = c.optString("year").ifBlank { null },
                    rating = c.optDouble("rating").takeIf { it > 0 },
                    backdropUrl = c.optString("backdrop").ifBlank { null },
                    posterUrl = c.optString("poster").ifBlank { null },
                    isTv = c.optBoolean("tv"),
                    serviceName = service,
                    packageName = pkg
                )
            })
        }
    } catch (e: Exception) { emptyList() }
}
