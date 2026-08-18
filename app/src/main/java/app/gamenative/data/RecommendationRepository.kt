package app.gamenative.data

import android.content.Context
import app.gamenative.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

object RecommendationRepository {

    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    internal val MOCK_HERO_JSON = """
        {
          "recommendation": null,
          "featured": {
            "campaignId": "mock-whisk",
            "title": "Whisk",
            "appId": 3602270,
            "developer": "Double Dusk Inc.",
            "heroImageUrl": "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3602270/92fb97a2832c9c075165c43d14d974c730ca716b/library_hero.jpg",
            "capsuleImageUrl": "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3602270/435512f90bdf39498f17fcbd103b19fa1223a430/library_capsule.jpg",
            "screenshots": [
              "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3602270/e0a52a09cd85472aecfb430ad086aae040cb100c/ss_e0a52a09cd85472aecfb430ad086aae040cb100c.1920x1080.jpg",
              "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3602270/77719570b08d1b46facf8477df20aa5b97b54573/ss_77719570b08d1b46facf8477df20aa5b97b54573.1920x1080.jpg",
              "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3602270/08fd286888a7efb1e782a5106fdb1b1f237bcdb2/ss_08fd286888a7efb1e782a5106fdb1b1f237bcdb2.1920x1080.jpg"
            ],
            "tags": ["Action", "Indie"],
            "status": "COMING_SOON",
            "description": {
              "en": "Whisk is a two-player platformer about shared movement and communication. Coordinate jumps, climbs and throws with a partner to get every Dreamcat home."
            },
            "actions": [
              { "type": "WISHLIST", "url": "https://store.steampowered.com/app/3602270/", "store": "Steam", "style": "primary" },
              { "type": "GET_DEMO", "url": "https://store.steampowered.com/app/3602270/", "appId": 4320000 },
              { "type": "VISIT", "url": "https://store.steampowered.com/app/3602270/" }
            ]
          }
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    // Latest featured from the most recent fetch. Kept in memory (not the disk cache) so the
    // featured decision is always live and never served stale from the daily recommendation cache.
    @Volatile private var lastFeatured: FeaturedItem? = null

    /**
     * Static recommendation + optional featured for the All-tab hero slot.
     * Always fetches so featured reflects the current campaign; the recommendation stays stable
     * for the day via its own cache; the cache is the offline fallback.
     */
    suspend fun getHero(context: Context): HeroResponse =
        withContext(Dispatchers.IO) {
            // OpenNative uses its bundled catalogue until it operates a first-party service.
            HeroResponse(
                recommendation = loadCachedRecommendation() ?: loadBundledFallback(context),
                featured = null,
            )
        }

    suspend fun getCurrentRecommendation(context: Context): RecommendedGame? =
        getHero(context).recommendation

    /** Latest featured (if any) — used by the detail screen; no network. */
    fun getCachedFeatured(): FeaturedItem? = lastFeatured

    fun getFeaturedGame(context: Context): RecommendedGame? =
        lastFeatured?.toRecommendedGame(context)

    /**
     * Keeps the recommendation stable for a day: reuses the cached pick until it goes stale,
     * then adopts (and caches) the freshly fetched one.
     */
    private fun stableRecommendation(fresh: RecommendedGame?): RecommendedGame? {
        val cached = loadCachedRecommendation()
        val cacheAgeMs = System.currentTimeMillis() - PrefManager.recommendationCacheTimestamp
        if (cached != null && cacheAgeMs in 0..CACHE_TTL_MS) return cached

        if (fresh != null) {
            PrefManager.recommendationCacheJson = json.encodeToString(fresh)
            PrefManager.recommendationCacheTimestamp = System.currentTimeMillis()
            return fresh
        }
        return cached
    }

    private fun loadCachedRecommendation(): RecommendedGame? {
        val cached = PrefManager.recommendationCacheJson
        if (cached.isEmpty()) return null
        return try {
            parseRecommendation(cached)
        } catch (e: Exception) {
            Timber.tag("RecommendationRepo").d(e, "Failed to parse cached recommendation")
            null
        }
    }

    private fun parseHero(body: String): HeroResponse? {
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("{") -> {
                val hero = runCatching { json.decodeFromString<HeroResponse>(body) }.getOrNull()
                if (hero != null && (hero.recommendation != null || hero.featured != null)) {
                    hero
                } else {
                    // Legacy: a single recommendation object.
                    runCatching { json.decodeFromString<RecommendedGame>(body) }
                        .getOrNull()
                        ?.let { HeroResponse(recommendation = it) }
                }
            }
            // Legacy: the old bare array response.
            trimmed.startsWith("[") ->
                json.decodeFromString<List<RecommendedGame>>(body).firstOrNull()
                    ?.let { HeroResponse(recommendation = it) }
            else -> null
        }
    }

    /** Cache holds a bare recommendation object; tolerate legacy hero/array shapes too. */
    private fun parseRecommendation(body: String): RecommendedGame? {
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("[") ->
                json.decodeFromString<List<RecommendedGame>>(body).firstOrNull()
            trimmed.startsWith("{") ->
                runCatching { json.decodeFromString<HeroResponse>(body).recommendation }.getOrNull()
                    ?: runCatching { json.decodeFromString<RecommendedGame>(body) }.getOrNull()
            else -> null
        }
    }

    private fun loadBundledFallback(context: Context): RecommendedGame? {
        return try {
            val body = context.assets.open("recommendations.json").bufferedReader().use { it.readText() }
            val list = json.decodeFromString<List<RecommendedGame>>(body)
            list.firstOrNull()
        } catch (e: Exception) {
            Timber.tag("RecommendationRepo").d(e, "Bundled recommendation fallback unavailable")
            null
        }
    }
}
