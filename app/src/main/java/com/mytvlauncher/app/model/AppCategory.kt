package com.mytvlauncher.app.model

import android.content.pm.ApplicationInfo

/** Filter chips on the Apps screen. */
enum class AppCategory(val label: String) {
    VIDEO("Video"),
    MUSIC("Music"),
    GAMES("Games"),
    TOOLS("Tools"),
    OTHER("Other")
}

/** Filter chips on the Live TV screen. */
enum class LiveTvTag(val label: String) {
    NEWS("News"),
    SPORTS("Sports"),
    ENTERTAINMENT("Entertainment")
}

/**
 * Sorts apps into categories. Android's own ApplicationInfo.category is
 * set by only some apps, so known Indian/global TV apps and keywords in
 * the package name or label are checked first, with the system category
 * as a fallback.
 */
object AppClassifier {

    private val musicWords = listOf(
        "music", "spotify", "gaana", "saavn", "wynk", "audible", "podcast",
        "radio", "soundcloud", "tidal", "hungama.music", "bsbportal"
    )

    private val videoWords = listOf(
        "netflix", "youtube", "primevideo", "prime video", "amazon.avod", "hotstar",
        "jiocinema", "jio cinema", "sonyliv", "zee5", "graymatrix", "mxplayer",
        "mx player", "vlc", "videolan", "plex", "kodi", "xbmc", "xstream", "jiotv",
        "jioplay", "tatasky", "tata play", "tataplay", "sunnxt", "sun nxt", "voot",
        "altbalaji", "erosnow", "apple.atve", "apple tv", "disney", "hbo", "mubi",
        "video", "player", "movie", "cinema", "stream", "iptv", "twitch",
        "hoichoi", "lionsgate", "discovery", "pluto"
    )

    private val toolWords = listOf(
        "file", "explorer", "manager", "browser", "chrome", "firefox", "downloader",
        "settings", "vpn", "cleaner", "launcher", "keyboard", "remote", "cast",
        "vending", "play store", "speedtest", "clock", "calculator", "backup",
        "installer", "airscreen", "send files", "x-plore", "teamviewer"
    )

    private val liveWords = listOf(
        "jiotv", "jioplay", "xstream", "tatasky", "tata play", "tataplay", "zee5",
        "graymatrix", "sonyliv", "sunnxt", "sun nxt", "hotstar", "jiocinema",
        "dishtv", "d2h", "news", "live", "aajtak", "ndtv", "republic", "abp",
        "indiatoday", "pluto", "yupp", "fancode", "iptv", "channels", "tv plus"
    )

    private val newsWords = listOf(
        "news", "aajtak", "ndtv", "republic", "abp", "indiatoday", "timesnow",
        "cnbc", "bbc", "cnn", "al jazeera", "aljazeera"
    )

    private val sportsWords = listOf(
        "sport", "fancode", "espn", "willow", "cricket", "jiocinema", "hotstar",
        "sonyliv", "fifa", "nba", "f1tv", "dazn"
    )

    fun category(packageName: String, label: String, info: ApplicationInfo): AppCategory {
        val haystack = "$packageName $label".lowercase()
        @Suppress("DEPRECATION")
        val flaggedGame = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        if (info.category == ApplicationInfo.CATEGORY_GAME || flaggedGame) return AppCategory.GAMES
        if (musicWords.any { it in haystack }) return AppCategory.MUSIC
        if (videoWords.any { it in haystack }) return AppCategory.VIDEO
        if (toolWords.any { it in haystack }) return AppCategory.TOOLS
        return when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS -> AppCategory.TOOLS
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.VIDEO
            else -> AppCategory.OTHER
        }
    }

    fun isLiveTvCandidate(packageName: String, label: String): Boolean {
        val haystack = "$packageName $label".lowercase()
        return liveWords.any { it in haystack }
    }

    fun liveTvTag(packageName: String, label: String): LiveTvTag {
        val haystack = "$packageName $label".lowercase()
        return when {
            newsWords.any { it in haystack } -> LiveTvTag.NEWS
            sportsWords.any { it in haystack } -> LiveTvTag.SPORTS
            else -> LiveTvTag.ENTERTAINMENT
        }
    }
}
